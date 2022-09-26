package decodes.hdb.algo;

import decodes.db.Constants;
import decodes.hdb.HdbFlags;
import decodes.hdb.dbutils.DBAccess;
import decodes.hdb.dbutils.DataObject;
import decodes.sql.DbKey;
import decodes.tsdb.CTimeSeries;
import decodes.tsdb.DbCompException;
import decodes.tsdb.ParmRef;
import decodes.tsdb.TimeSeriesIdentifier;
import decodes.tsdb.algo.AWAlgoType;
import decodes.util.PropertySpec;
import ilex.util.TextUtil;
import ilex.var.NamedVariable;
import ilex.var.TimedVariable;
import opendcs.dai.TimeSeriesDAI;

import java.sql.Connection;
import java.util.*;
import java.util.regex.Pattern;

import static decodes.tsdb.VarFlags.TO_WRITE;
import static java.lang.Math.abs;

//AW:IMPORTS
// Place an import statements you need here.
//AW:IMPORTS_END

//AW:JAVADOC

/**
 This algorithm computes the average ratio between all county hucs in the basin for the input timeseries
 and the total for the basin for use in estimating the
 distribution of the timeseries when only the basin total is known.
 Only applies to R_YEAR

 ROUNDING: determines if rounding to the 7th decimal point is desired, default FALSE

 Algorithm is as follows:
 query average ratio of one timeseries to the total
 store value in r_year 1985 by convention, set by computation output variables
 decide if want to check for update or just have database catch duplicates

 working SQL query to compute data:
 with s as
 ( select vals.value, peer.site_id, EXTRACT(YEAR FROM vals.start_date_time) yr
 from
 r_year vals, hdb_site_datatype peersd, hdb_site peer, hdb_site_datatype trigsd, hdb_site trig
 where
 vals.site_datatype_id = peersd.site_datatype_id and peersd.site_id = peer.site_id and
 trigsd.site_id = trig.site_id and peer.basin_id = trig.basin_id and
 trig.objecttype_id = peer.objecttype_id and
 trigsd.datatype_id = peersd.datatype_id and
 trigsd.site_datatype_id =   33571   and
 EXTRACT(YEAR FROM vals.start_date_time) between 1986 and 1995
 ), t as
 (select yr, sum(s.value) total from s group by yr)
 SELECT avg(s.value/t.total) ratio
 , outts.ts_id outid
 from s, t, hdb_site_datatype outsd, hdb_site_datatype ratiosd, cp_ts_id outts
 where
 s.yr = t.yr and
 s.site_id = outsd.site_id and
 outsd.datatype_id = ratiosd.datatype_id and
 outsd.site_datatype_id = outts.site_datatype_id and
 outts.interval = 'year' AND
 outts.table_selector = 'R_' AND
 ratiosd.site_datatype_id =   34527
 group by outts.ts_id ;

 querying r_year for actual values to ensure validation has occurred.


 */
//AW:JAVADOC_END
public class CULStateTribLivestockRatioCompute
        extends decodes.tsdb.algo.AW_AlgorithmBase
{
    //AW:INPUTS
    public double livestock;	//AW:TYPECODE=i
    String[] _inputNames = { "livestock" };
//AW:INPUTS_END

    //AW:LOCALVARS
    // Enter any local class variables needed by the algorithm.
    String alg_ver = "1.0";
    String query;
    boolean do_setoutput = true;
    Connection conn = null;
    TimeSeriesDAI dao;
    HashMap<String, CTimeSeries> outputSeries = new HashMap<String, CTimeSeries>();

    PropertySpec[] specs =
            {
                    new PropertySpec("rounding", PropertySpec.BOOLEAN,
                            "(default=false) If true, then rounding is done on the output value."),
                    new PropertySpec("validation_flag", PropertySpec.STRING,
                            "(empty) Always set this validation flag in the output."),
                    new PropertySpec("flags", PropertySpec.STRING,
                            "(empty) Always set these dataflags in the output."),
                    new PropertySpec("src_startyr", PropertySpec.INT,
                    		"(1986) Start year of source data used to calculate disagg coefficients"),
                    new PropertySpec("src_endyr", PropertySpec.INT,
                    		"(1995) End year of source data used to calculate disagg coefficients"),
                    new PropertySpec("coeff_year", PropertySpec.INT,
                            "(1985) What year to write coefficients into"),
            };



//AW:LOCALVARS_END

    //AW:OUTPUTS
    public NamedVariable ratio = new NamedVariable("ratio", 0);
    String[] _outputNames = { "ratio"};
//AW:OUTPUTS_END

    //AW:PROPERTIES
    public boolean rounding = false;
    public String validation_flag = "";
    public long coeff_year = 1985;
    public long src_startyr = 1986;
    public long src_endyr = 1995;
    public String flags = "";

    String[] _propertyNames = { "validation_flag", "rounding", "coeff_year", "flags", "src_startyr", "src_endyr" };
//AW:PROPERTIES_END

    // Allow javac to generate a no-args constructor.

    /**
     * Algorithm-specific initialization provided by the subclass.
     */
    protected void initAWAlgorithm( )
            throws DbCompException
    {
//AW:INIT
        _awAlgoType = AWAlgoType.TIME_SLICE; // doesn't matter and don't want automatic deletes
//AW:INIT_END

//AW:USERINIT
//AW:USERINIT_END
    }

    /**
     * This method is called once before iterating all time slices.
     */
    protected void beforeTimeSlices()
            throws DbCompException
    {
//AW:BEFORE_TIMESLICES
        // This code will be executed once before each group of time slices.
        // For TimeSlice algorithms this is done once before all slices.
        // For Aggregating algorithms, this is done before each aggregate
        // period.

        query = null;
        do_setoutput = true;
        conn = null;
        dao = tsdb.makeTimeSeriesDAO();

//AW:BEFORE_TIMESLICES_END
    }

    /**
     * Do the algorithm for a single time slice.
     * AW will fill in user-supplied code here.
     * Base class will set inputs prior to calling this method.
     * User code should call one of the setOutput methods for a time-slice
     * output variable.
     *
     * @throws DbCompException (or subclass thereof) if execution of this
     *        algorithm is to be aborted.
     */
    protected void doAWTimeSlice()
            throws DbCompException
    {
//AW:TIMESLICE
        // Enter code to be executed at each time-slice.
//AW:TIMESLICE_END
    }

    /**
     * This method is called once after iterating all time slices.
     */
    protected void afterTimeSlices()
            throws DbCompException
    {
//AW:AFTER_TIMESLICES
        // This code will be executed once after each group of time slices.
        // For TimeSlice algorithms this is done once after all slices.
        // For Aggregating algorithms, this is done after each aggregate
        // period.
        // calculate number of days in the month in case the numbers are for month derivations
        debug1(comp.getAlgorithmName()+"-"+alg_ver+" BEGINNING OF AFTER TIMESLICES, SDI: " + getSDI("livestock"));
        do_setoutput = true;
        ParmRef liveRef = getParmRef("livestock");

        // get the input and output parameters and see if its model data
        if (liveRef == null) {
            warning("Unknown variable 'livestock'");
            return;
        }

        DbKey liveSDI = liveRef.timeSeries.getSDI();

        String input_interval1 = liveRef.compParm.getInterval();
        if (input_interval1 == null || !input_interval1.equals("year"))
            warning("Wrong input1 interval for " + comp.getAlgorithmName());
        String table_selector1 = liveRef.compParm.getTableSelector();
        if (table_selector1 == null || !table_selector1.equals("R_"))
            warning("Invalid table selector for algorithm, only R_ supported");

        ParmRef ratioRef = getParmRef("ratio");
        if (ratioRef == null) {
            warning("Unknown output variable 'ratio'");
            return;
        }
        DbKey ratioSDI = ratioRef.timeSeries.getSDI();

        TimeZone tz = TimeZone.getTimeZone("MST");
        GregorianCalendar cal = new GregorianCalendar(tz);
        //always setting this output in the year set by user
        cal.set((int)coeff_year, 0, 1, 0, 0); // Months are 0 indexed in Java dates

        // get the connection  and a few other classes so we can do some sql
        conn = tsdb.getConnection();

        String status;
        DataObject dbobj = new DataObject();
        dbobj.put("ALG_VERSION",alg_ver);
        DBAccess db = new DBAccess(conn);
        TimeSeriesDAI dao = tsdb.makeTimeSeriesDAO();

        String select_clause = "SELECT avg(s.value/t.total) ratio ";

        if (rounding)
        {
            select_clause = "SELECT round(avg(s.value/t.total),11) ratio "; // 7 used by other HDB aggregates, these values get small though!
        }

        query = " with s as " +
                "( select vals.value, peer.site_id, EXTRACT(YEAR FROM vals.start_date_time) yr " +
                "from " +
                "r_year vals, hdb_site_datatype peersd, hdb_site peer, hdb_site_datatype trigsd, hdb_site trig " +
                "where " +
                "vals.site_datatype_id = peersd.site_datatype_id and peersd.site_id = peer.site_id and  " +
                "trigsd.site_id = trig.site_id and peer.basin_id = trig.basin_id and " +
                "trig.objecttype_id = peer.objecttype_id and " +
                "trigsd.datatype_id = peersd.datatype_id and " +
                "trigsd.site_datatype_id = " + liveSDI + " and " +
                "EXTRACT(YEAR FROM vals.start_date_time) between " + src_startyr + " AND " + src_endyr + " " +
                "), t as " +
                "(select yr, sum(s.value) total from s group by yr) " +
                select_clause +
                ", outts.ts_id ts " +
                "from s, t, hdb_site_datatype outsd, hdb_site_datatype ratiosd, cp_ts_id outts " +
                "where " +
                "s.yr = t.yr and " +
                "s.site_id = outsd.site_id and " +
                "outsd.datatype_id = ratiosd.datatype_id and " +
                "outsd.site_datatype_id = outts.site_datatype_id and " +
                "outts.interval = '" + getInterval("ratio") + "' and " +
                "outts.table_selector = '" + getTableSelector("ratio") + "' and " +
                "ratiosd.site_datatype_id = " + ratioSDI + " " +
                "group by outts.ts_id ";


        status = db.performQuery(query,dbobj); // interface has no methods for parameters

        debug3(" SQL STRING:" + query + "   DBOBJ: " + dbobj.toString() + "STATUS:  " + status);
        // now see if the aggregate query worked if not abort!!!

        int count = Integer.parseInt(dbobj.get("rowCount").toString());
        if (status.startsWith("ERROR") || count < 1 || !findOutputSeries(dbobj))
        {
            warning(comp.getName()+"-"+alg_ver+" Aborted: see following error message");
            warning(status);
            return;
        }

        ArrayList<Object> ratios;
        ArrayList<Object> tsids;
        Object r = dbobj.get("ratio");
        Object t = dbobj.get("ts");
        if (count == 1)
        {
            ratios = new ArrayList<Object>();
            tsids = new ArrayList<Object>();
            ratios.add(r);
            tsids.add(t);
        }
        else
        {
            ratios = (ArrayList<Object>)r;
            tsids = (ArrayList<Object>)t;
        }

        double total = 0;
        int flag = TO_WRITE;
        if (!flags.isEmpty())
            flag |= HdbFlags.hdbDerivation2flag(flags);
        if (!validation_flag.isEmpty())
            flag |= HdbFlags.hdbValidation2flag(validation_flag.charAt(1));
        if (tsdb.isHdb() && TextUtil.str2boolean(comp.getProperty("OverwriteFlag")))
        {
            //waiting on release of overwrite flag upstream
            //flag |= HdbFlags.HDBF_OVERWRITE_FLAG);
        }

        Iterator<Object> itR = ratios.iterator();
        Iterator<Object> itT = tsids.iterator();
        try {
            while(itR.hasNext() && itT.hasNext()) {
                String id = itT.next().toString();
                debug3("Output TS ID: " + id);
                double ratio_out = new Double(itR.next().toString());
                debug3(comp.getName() + "-" + alg_ver + " Setting output for year: " + debugSdf.format(cal.getTime()) +
                        " ratio: " + ratio_out);
                TimedVariable tv = new TimedVariable(cal.getTime(), ratio_out, flag);
                total += ratio_out;
                outputSeries.get(id).addSample(tv);
            }
        } catch (Exception e) {
            warning(e.toString());
            return;
        }
        if (abs(total - 1.0D) > 1e-7) {
            warning("Total basin ratios did not sum to 1.0! Ids: " + Arrays.toString(tsids.toArray()));
        }

        //waiting on release of overwrite flag upstream, may want this to trigger recomputation of disagg?
        //flag |= HdbFlags.HDBF_OVERWRITE_FLAG);
        TimedVariable tv = new TimedVariable(cal.getTime(), total, flag);
        ratioRef.timeSeries.addSample(tv);
        outputSeries.put(ratioRef.tsid.toString(),ratioRef.timeSeries);

        for (Map.Entry<String, CTimeSeries> entry : outputSeries.entrySet()) {
            String k = entry.getKey();
            CTimeSeries v = entry.getValue();
            try {
                debug1(comp.getName() + "-" + alg_ver + "saving tsid: " + k + " timeseries: " + v.getTimeSeriesIdentifier());
                v.setComputationId(comp.getId());
                dao.saveTimeSeries(v);
            } catch (Exception e) {
                warning("Exception during saving output to database:" + e);
            }
        }
        outputSeries.clear();

        // don't need to complain that ratio can't be saved from non-aggregating, we already saved it
        ratioRef.timeSeries.deleteAll();

        dao.close();
//AW:AFTER_TIMESLICES_END
    }

    /**
     * runs supplied query for output timeseries site ids and ts_ids, updates global outputSeries hashmap
     * @param dbobj result from a db query
     * @return false if failed
     */
    private boolean findOutputSeries(DataObject dbobj) {

        // Need to handle multiple TS_IDs!
        int count = Integer.parseInt(dbobj.get("rowCount").toString());
        ArrayList<Object> tsids;

        if (count == 0)
        {
            warning(comp.getName() + "-" + alg_ver + " Aborted: zero output TS_IDs");
            return false;
        }
        else if (count == 1)
        {
            tsids = new ArrayList<Object>();
            tsids.add(dbobj.get("ts"));
        }
        else
        {
            tsids = (ArrayList<Object>) dbobj.get("ts");
        }

        Iterator<Object> itId = tsids.iterator();
        try {
            while(itId.hasNext()) {
                DbKey id = DbKey.createDbKey(Long.parseLong(itId.next().toString()));
                debug3("Output Timeseries ID: " + id);
                TimeSeriesIdentifier tsid = dao.getTimeSeriesIdentifier(id);
                outputSeries.putIfAbsent(id.toString(),dao.makeTimeSeries(tsid));
            }
        } catch (Exception e) {
            warning(e.toString());
            return false;
        }
        return true;
    }

    /**
     * Required method returns a list of all input time series names.
     */
    public String[] getInputNames()
    {
        return _inputNames;
    }

    /**
     * Required method returns a list of all output time series names.
     */
    public String[] getOutputNames()
    {
        return _outputNames;
    }

    /**
     * Required method returns a list of properties that have meaning to
     * this algorithm.
     */
    public String[] getPropertyNames()
    {
        return _propertyNames;
    }

    @Override
    protected PropertySpec[] getAlgoPropertySpecs()
    {
        return specs;
    }

}