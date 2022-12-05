package decodes.hdb.algo;

import java.sql.Connection;
import java.util.Date;

import ilex.var.NamedVariableList;
import ilex.var.NamedVariable;
import decodes.tsdb.DbAlgorithmExecutive;
import decodes.tsdb.DbCompException;
import decodes.tsdb.DbIoException;
import decodes.tsdb.VarFlags;
import decodes.tsdb.algo.AWAlgoType;
import decodes.util.PropertySpec;
import decodes.tsdb.CTimeSeries;
import decodes.tsdb.ParmRef;
import ilex.var.TimedVariable;
import decodes.tsdb.TimeSeriesIdentifier;
import decodes.tsdb.IntervalIncrement;

//AW:IMPORTS
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TimeZone;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import decodes.hdb.dbutils.DBAccess;
import decodes.hdb.dbutils.DataObject;
import decodes.sql.DbKey;
import decodes.tsdb.IntervalCodes;
import decodes.tsdb.ParmRef;
import ilex.var.TimedVariable;
import ilex.var.NoConversionException;
//AW:IMPORTS_END

//AW:JAVADOC
/**


 */
//AW:JAVADOC_END
public class CULPowerTemporalDisagg
	extends decodes.tsdb.algo.AW_AlgorithmBase
{
//AW:INPUTS
	public double AnnualInput;	//AW:TYPECODE=i
	public double CoefficientInput;
	String _inputNames[] = { "AnnualInput" , "CoefficientInput"};
//AW:INPUTS_END

//AW:LOCALVARS
	ParmRef Annual_iref = null;
	ParmRef Coefficient_iref = null;
	
	DbKey compID = null;
	DbKey CUL_SDI = null;
	DbKey Coeff_SDI = null;

	Connection conn = null;
	
    PropertySpec[] specs =
        {
        		new PropertySpec("coeff_year", PropertySpec.INT,
                        "(1985) What year to read coefficients from, currently unused!"),
        };

//AW:LOCALVARS_END

//AW:OUTPUTS
	public NamedVariable MonthlyOutput = new NamedVariable("MonthlyOutput", 0);
	String _outputNames[] = { "MonthlyOutput" };
//AW:OUTPUTS_END

//AW:PROPERTIES
    public long coeff_year = 1985;
	String _propertyNames[] = { "coeff_year" };
//AW:PROPERTIES_END

	// Allow javac to generate a no-args constructor.

	/**
	 * Algorithm-specific initialization provided by the subclass.
	 */
	protected void initAWAlgorithm( )
		throws DbCompException
	{
//AW:INIT
		_awAlgoType = AWAlgoType.TIME_SLICE;
//AW:INIT_END

//AW:USERINIT
		// Code here will be run once, after the algorithm object is created.
//AW:USERINIT_END
	}
	
	/**
	 * This method is called once before iterating all time slices.
	 */
	protected void beforeTimeSlices()
		throws DbCompException
	{
//AW:BEFORE_TIMESLICES
		Annual_iref = getParmRef("AnnualInput");
		Coefficient_iref = getParmRef("CoefficientInput");
		
		compID = comp.getId();
		CUL_SDI = Annual_iref.timeSeries.getSDI();
		Coeff_SDI = Coefficient_iref.timeSeries.getSDI();
        
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
		
		
		// Query related variables
	    String status;
	    String query;
	    DataObject dbobj = new DataObject();
		conn = tsdb.getConnection();
		DBAccess db = new DBAccess(conn);
		
      
        // Get IDs of compedit and cu_estimation process loading application
		// For now, source data is anything that doesn't come from either of these loading apps
        query = "SELECT loading_application_id FROM hdb_loading_application"
        		+ " WHERE loading_application_name = 'CU_FillMissing'";  

        status = this.doQuery(query, dbobj, db);
        int fillMissingApp = Integer.valueOf((String) dbobj.get("loading_application_id"));
        
        query = "SELECT loading_application_id FROM hdb_loading_application"
        		+ " WHERE loading_application_name = 'CU_Agg_Disagg'";  

        status = this.doQuery(query, dbobj, db);
        int estimationApp = Integer.valueOf((String) dbobj.get("loading_application_id"));
        
        // Get years to disagg (years with an annual source data point)
        // May need to get more thorough for this in other sectors
        query = "SELECT EXTRACT(YEAR FROM start_date_time) year, value FROM r_base"
				+ " WHERE site_datatype_id = " + CUL_SDI.getValue()
				+ " AND interval = 'year'"
				+ " AND loading_application_id != " + fillMissingApp
				+ " AND loading_application_id != " + estimationApp
        		+ " ORDER BY EXTRACT(YEAR FROM start_date_time) ASC";
        
        status = this.doQuery(query, dbobj, db);
        int count = Integer.parseInt(dbobj.get("rowCount").toString());
        
        ArrayList<Object> YearstoDisagg;
        ArrayList<Object> AnnualSourceData;
        if (count == 0) {
        	warning(comp.getAlgorithmName() + " Aborted: see following error message");
        	warning("No annual values to disagg for SDI " + getSDI("input"));
        	return;
        }
        else if (count == 1) {
        	YearstoDisagg = new ArrayList<Object>();
        	AnnualSourceData = new ArrayList<Object>();
        	
        	YearstoDisagg.add(dbobj.get("year"));
        	AnnualSourceData.add(dbobj.get("value"));
        }
        else {
            YearstoDisagg = (ArrayList<Object>) dbobj.get("year");
            AnnualSourceData = (ArrayList<Object>) dbobj.get("value");
        }
        	
        

        
		// Get monthly coefficients
        // Right now getting all rows works, but should probably make it smarter to look at a specific year, or at least confirm there are 12 values
		query = "SELECT value FROM r_base"
				+ " WHERE site_datatype_id = " + Coeff_SDI.getValue()
				+ " ORDER BY start_date_time";
		status = this.doQuery(query, dbobj, db);
		ArrayList<Object> monthlyCoefficients = (ArrayList<Object>) dbobj.get("value");
		if(monthlyCoefficients.size() != 12)
		{
			throw new DbCompException("Something wrong with monthly coefficients in query: " + query); // improve error handling
		}

		
        // Loop through years to disagg
        TimeZone tz = TimeZone.getTimeZone("MST");
        GregorianCalendar cal = new GregorianCalendar(tz);
        Iterator<Object> itYr = YearstoDisagg.iterator();
        Iterator<Object> itAnnualSource = AnnualSourceData.iterator();
        
        while(itYr.hasNext() && itAnnualSource.hasNext()) {
        	int yr = Integer.parseInt(itYr.next().toString());
        	Double annualSourceVal = Double.valueOf(itAnnualSource.next().toString());
        	
        	// get Monthly source data
            query = "SELECT EXTRACT(MONTH FROM start_date_time) month, value FROM r_base"
    				+ " WHERE site_datatype_id = " + CUL_SDI.getValue()
    				+ " AND interval = 'month'"
    				+ " AND EXTRACT(YEAR FROM start_date_time) = " + yr    				
    				+ " AND loading_application_id != " + fillMissingApp
    				+ " AND loading_application_id != " + estimationApp
            		+ " ORDER BY EXTRACT(MONTH FROM start_date_time) ASC";
            this.doQuery(query, dbobj, db);
        	
            // Volume to distribute is the difference between the annual source point and sum of monthly source points
            // Monthly volume (for months without source data) = Volume to distribute * monthly coefficient / sum of monthly coefficients w/o source data
            ArrayList<Object> monthlySourceData = null;
            ArrayList<Object> monthlySourceMonths = null;
            Double monthlySourceSum = 0.0;
            Double monthlyCoefficientSum = 0.0;
            if(dbobj.get("value").toString().isEmpty())
            {
            	monthlySourceData = null;
            	monthlySourceMonths = null;
            }else
            {
            	monthlySourceData = (ArrayList<Object>) dbobj.get("value");
            	monthlySourceMonths = (ArrayList<Object>) dbobj.get("month");
            }
            
            // First loop through the months to calculate sums
            for(int m = 1; m <= 12; m++)
            {
            	if(monthlySourceMonths != null && monthlySourceMonths.contains(String.valueOf(m)))
            	{
            		int idx = monthlySourceMonths.indexOf(String.valueOf(m));
            		monthlySourceSum += Double.valueOf(monthlySourceData.get(idx).toString());
            	}else
            	{
            		monthlyCoefficientSum += Double.valueOf(monthlyCoefficients.get(m - 1).toString());
            	}
            }
         // Loop through months again to set output
            for(int m = 1; m <= 12; m++)
            {
            	if(monthlySourceMonths == null || !monthlySourceMonths.contains(String.valueOf(m)))
            	{
            		cal.set(yr, m - 1,1,0,0);
            		double coeff = Double.valueOf(monthlyCoefficients.get(m - 1).toString());
            		setOutput(MonthlyOutput,(annualSourceVal - monthlySourceSum) * coeff / monthlyCoefficientSum,cal.getTime());
            	}
            }
                                    
        }
       

//AW:AFTER_TIMESLICES_END
	}

	private String doQuery(String q, DataObject dbobj, DBAccess db)
	{
		String status = db.performQuery(q, dbobj);
		if(status.startsWith("ERROR"))
		{
			System.out.println("Query didn't work"); // improve error handling
		}
		return status;
		
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
