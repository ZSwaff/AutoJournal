package com.auriferous.autojournal;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;


public class RecalcService extends IntentService {
    public static final String ACTION_RECALC_METADATA = "com.auriferous.autojournal.action.RECALC_METADATA";
    public static final String ACTION_UPDATE_ERROR_FILE = "com.auriferous.autojournal.action.UPDATE_ERROR_FILE";
    public static final String ACTION_UPDATE_ALL_ERROR_FILES = "com.auriferous.autojournal.action.UPDATE_ALL_ERROR_FILES";
    public static final String ACTION_UPDATE_TRAVEL_CIRCLE = "com.auriferous.autojournal.action.UPDATE_TRAVEL_CIRCLE";
    public static final String ACTION_UPDATE_ALL_TRAVEL_CIRCLES = "com.auriferous.autojournal.action.UPDATE_ALL_TRAVEL_CIRCLES";

    public RecalcService() {
        super("RecalcService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;

        final String action = intent.getAction();
        if (ACTION_RECALC_METADATA.equals(action)) {
            recalcMetadata();
            MainActivity.justUpdated = true;
            return;
        }
        if (ACTION_UPDATE_ERROR_FILE.equals(action)) {
            updateErrorFile();
            MainActivity.justUpdated = true;
            return;
        }
        if (ACTION_UPDATE_ALL_ERROR_FILES.equals(action)) {
            updateAllErrorFiles();
            MainActivity.justUpdated = true;
            return;
        }
        if (ACTION_UPDATE_TRAVEL_CIRCLE.equals(action)) {
            updateTravelCircle();
            return;
        }
        if (ACTION_UPDATE_ALL_TRAVEL_CIRCLES.equals(action)) {
            updateAllTravelCircles();
            return;
        }
    }

    private void recalcMetadata() {
        File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
        File metadataFile = new File(baseRoot, "Metadata.txt");

        metadataFile.delete();

        boolean isFirst = true;

        int totalUs = 0;
        String firstU = "First ::  ";
        String lastU = "Last ::  ";

        boolean started = false;
        for(int year = 2000;;year++)
        {
            File yearRoot = new File(baseRoot, ""+year);
            if(!yearRoot.exists())
            {
                if(!started) continue;
                break;
            }
            started = true;
            for(String month : MainActivity.monthReference)
            {
                File monthRoot = new File(yearRoot, month);
                if(!monthRoot.exists()) continue;
                for(int dayOM = 1; dayOM <= 31; dayOM++)
                {
                    File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
                    if(!dayFile.exists()) continue;
                    ArrayList<String> dayData = Reader.readFile(dayFile);
                    if(isFirst)
                    {
                        firstU += dayData.get(2).substring(0, 8) + " on " + dayData.get(0);
                        isFirst = false;
                    }
                    lastU = "Last ::  " + dayData.get(dayData.size()-1).substring(0, 8) + " on " + dayData.get(0);
                    int linesInFile = dayData.size();
                    if(linesInFile!=0) totalUs += (linesInFile-2);
                }
            }
        }

        String strTotalUs = "Total ::  "+Converter.intToStringNicely(totalUs);

        Writer.writeToTextFile(metadataFile, firstU+"\n\n"+lastU+"\n\n"+strTotalUs);
    }

    private void updateErrorFile(int year, int monthIndex) {
        //make monthly error file. display most recent on view if it's not null
        File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
        File yearRoot = new File(baseRoot, ""+year);
        if (!yearRoot.exists()) return;
        File monthRoot = new File(yearRoot, MainActivity.monthReference[monthIndex]);
        if (!monthRoot.exists()) return;
        File errorLog = new File(monthRoot, "Errors.txt");
        if(errorLog.exists()) errorLog.delete();

        int totUpdatesMissed = 0;

        ArrayList<String> errors = new ArrayList<>();
        String startProblem = "01-00:00:00";
        int updatesMissed = 0;
        boolean currentProblem = false;
        int dayOM = 1;

        Calendar cal = Calendar.getInstance();

        for(; dayOM <= 31; dayOM++)
        {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthIndex);
            cal.set(Calendar.DAY_OF_MONTH, dayOM);
            if(cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != monthIndex || cal.get(Calendar.DAY_OF_MONTH) != dayOM) continue;

            File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
            if(!dayFile.exists()) {
                updatesMissed += (24*60)/5;
                currentProblem = true;
                continue;
            }

            ArrayList<String> dayData = Reader.readFile(dayFile); dayData.remove(0); dayData.remove(0);
            int curHour = 0;
            int curMinute = 0;
            String nextTime = dayData.get(0).substring(0, 8); dayData.remove(0);

            while(curHour < 24){
                int timeGap = Converter.calculateTimeBetween(nextTime, curHour, curMinute);

                if(timeGap <= 240){
                    if(timeGap > -60){
                        if(currentProblem){
                            //a problem just ended
                            String endTime = Converter.timeValuesToString(dayOM, curHour, curMinute);
                            errors.add(String.format("%04d", updatesMissed) + " between " + startProblem + " and "+ endTime);
                            totUpdatesMissed += updatesMissed;
                            updatesMissed = 0;
                            currentProblem = false;
                        }
                    }
                    if(dayData.size() == 0) {
                        if(curHour != 23 || curMinute != 55){
                            updatesMissed = 12 * (23 - curHour) + (55 - curMinute)/5;
                            startProblem = Converter.timeValuesToString(dayOM, curHour, curMinute);
                            currentProblem = true;
                        }
                        break;
                    }
                    nextTime = dayData.get(0).substring(0, 8); dayData.remove(0);
                    if(timeGap <= -60) curMinute -=5;
                }
                else {
                    updatesMissed++;
                    if(!currentProblem){
                        startProblem = Converter.timeValuesToString(dayOM, curHour, curMinute);
                        currentProblem = true;
                    }
                }

                //advance time
                curMinute += 5;
                if(curMinute == 60){
                    curMinute = 0;
                    curHour++;
                }
            }
        }
        dayOM--;

        long curTime = Calendar.getInstance().getTimeInMillis();
        cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthIndex);
        cal.set(Calendar.DAY_OF_MONTH, dayOM);
        long monthEndTime = cal.getTimeInMillis();

        if(currentProblem && (curTime > monthEndTime)){
            String endTime = (dayOM<10?"0":"")+dayOM+"-23:55:00";
            errors.add(String.format("%04d", updatesMissed) + " between " + startProblem + " and "+ endTime);
            totUpdatesMissed += updatesMissed;
        }

        String allErrors = "";
        for(String err : errors)
            allErrors += err + "\n";
        Writer.writeToTextFile(errorLog, "Error report for "+MainActivity.monthReference[monthIndex].substring(3)+" "+year+": "+totUpdatesMissed +" total\n\n" + allErrors);
    }
    private void updateErrorFile() {
        Calendar cal = Calendar.getInstance();
        updateErrorFile(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
    }
    private void updateAllErrorFiles() {
        boolean started = false;
        for(int year = 2000;;year++)
        {
            File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
            File yearRoot = new File(baseRoot, ""+year);
            if(yearRoot.exists()) started = true;
            else if(started) break;

            for(int iMonth = 0; iMonth <= 11; iMonth++)
                updateErrorFile(year, iMonth);
        }
    }

    private void updateTravelCircle(int year, int monthIndex){
        File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
        File yearRoot = new File(baseRoot, ""+year);
        if (!yearRoot.exists()) return;
        File monthRoot = new File(yearRoot, MainActivity.monthReference[monthIndex]);
        if (!monthRoot.exists()) return;
        File travelCircleLog = new File(monthRoot, "Travel Circles.txt");
        if(travelCircleLog.exists()) travelCircleLog.delete();

        ArrayList<String> travelCircles = new ArrayList<>();
        Calendar cal = Calendar.getInstance();

        for(int dayOM = 1; dayOM <= 31; dayOM++)
        {
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, monthIndex);
            cal.set(Calendar.DAY_OF_MONTH, dayOM);
            if(cal.get(Calendar.YEAR) != year || cal.get(Calendar.MONTH) != monthIndex || cal.get(Calendar.DAY_OF_MONTH) != dayOM) continue;

            File dayFile = new File(monthRoot, (dayOM<10?"0":"")+dayOM+".txt");
            if(!dayFile.exists()) continue;

            ArrayList<String> fileContents = Reader.readFile(dayFile);
            fileContents.remove(0); fileContents.remove(0);

            ArrayList<Location> allLocs = new ArrayList<>();
            for(String line : fileContents){
                String locStr = line.split(" :: ")[1].trim();
                allLocs.add(Converter.stringToLoc(locStr));
            }

            try{
                Area dayTravelCircle = new Area("", allLocs);
                travelCircles.add(dayOM + "  ::" + dayTravelCircle.toString());
            }
            catch(Exception e){
                travelCircles.add(dayOM + "  ::" + "  null");
            }
        }

        String allCircles = "";
        for(String circ : travelCircles)
            allCircles += circ + "\n";
        Writer.writeToTextFile(travelCircleLog, "Travel circle report for "+MainActivity.monthReference[monthIndex].substring(3)+" "+year+"\n\n" + allCircles);

    }
    private void updateTravelCircle(){
        Calendar cal = Calendar.getInstance();
        updateTravelCircle(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH));
    }
    private void updateAllTravelCircles(){
        boolean started = false;
        for(int year = 2000;;year++)
        {
            File baseRoot = new File(Environment.getExternalStorageDirectory(), "Location Logs");
            File yearRoot = new File(baseRoot, ""+year);
            if(yearRoot.exists()) started = true;
            else if(started) break;

            for(int iMonth = 0; iMonth <= 11; iMonth++)
                updateTravelCircle(year, iMonth);
        }
    }
}
