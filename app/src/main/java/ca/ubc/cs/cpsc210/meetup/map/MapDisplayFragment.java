package ca.ubc.cs.cpsc210.meetup.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONException;
import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.OverlayManager;
import org.osmdroid.views.overlay.PathOverlay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;



import ca.ubc.cs.cpsc210.meetup.R;
import ca.ubc.cs.cpsc210.meetup.exceptions.IllegalCourseTimeException;
import ca.ubc.cs.cpsc210.meetup.model.Building;
import ca.ubc.cs.cpsc210.meetup.model.Course;
import ca.ubc.cs.cpsc210.meetup.model.CourseFactory;
import ca.ubc.cs.cpsc210.meetup.model.EatingPlace;
import ca.ubc.cs.cpsc210.meetup.model.Place;
import ca.ubc.cs.cpsc210.meetup.model.PlaceFactory;
import ca.ubc.cs.cpsc210.meetup.model.Schedule;
import ca.ubc.cs.cpsc210.meetup.model.Section;
import ca.ubc.cs.cpsc210.meetup.model.Student;
import ca.ubc.cs.cpsc210.meetup.model.StudentManager;
import ca.ubc.cs.cpsc210.meetup.util.CourseTime;
import ca.ubc.cs.cpsc210.meetup.util.LatLon;
import ca.ubc.cs.cpsc210.meetup.util.SchedulePlot;

/**
 * Fragment holding the map in the UI.
 */
public class MapDisplayFragment extends Fragment {

    /**
     * Log tag for LogCat messages
     */
    private final static String LOG_TAG = "MapDisplayFragment";

    /**
     * Preference manager to access user preferences
     */
    private SharedPreferences sharedPreferences;

    /**
     * String to know whether we are dealing with MWF or TR schedule.
     * You will need to update this string based on the settings dialog at appropriate
     * points in time. See the project page for details on how to access
     * the value of a setting.
     */
    private String activeDay = "MWF";

    /**
     * A central location in campus that might be handy.
     */
    private final static GeoPoint UBC_MARTHA_PIPER_FOUNTAIN = new GeoPoint(49.264865,
            -123.252782);

    /**
     * Meetup Service URL
     * CPSC 210 Students: Complete the string.
     */
    private final String getStudentURL = "";

    /**
     * FourSquare URLs. You must complete the client_id and client_secret with values
     * you sign up for.
     */
    private static String FOUR_SQUARE_URL = "https://api.foursquare.com/v2/venues/explore";
    private static String FOUR_SQUARE_CLIENT_ID = "N0IGQLNOLBXBK3KJCAH1NIXA5NIPVLKNJI4SBATNKNXUWY1D";
    private static String FOUR_SQUARE_CLIENT_SECRET = "13ZCYCZIUCZYVBZYU5ZREQXLNTBOW0XDFQKG3ETJHQEUNOKL";


    /**
     * Overlays for displaying my schedules, buildings, etc.
     */
    private List<PathOverlay> scheduleOverlay;
    private ItemizedIconOverlay<OverlayItem> buildingOverlay;
    private OverlayItem selectedBuildingOnMap;

    /**
     * View that shows the map
     */
    private MapView mapView;

    /**
     * Access to domain model objects. Only store "me" in the studentManager for
     * the base project (i.e., unless you are doing bonus work).
     */
    private StudentManager studentManager;
    private Student randomStudent = null;
    private Student me = null;
    private static int ME_ID = 999999;

    /**
     * Map controller for zooming in/out, centering
     */
    private IMapController mapController;

    // ******************** Android methods for starting, resuming, ...

    // You should not need to touch this method
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        scheduleOverlay = new ArrayList<PathOverlay>();

        // You need to setup the courses for the app to know about. Ideally
        // we would access a web service like the UBC student information system
        // but that is not currently possible
        initializeCourses();

        // Initialize the data for the "me" schedule. Note that this will be
        // hard-coded for now
        initializeMySchedule();

        // You are going to need an overlay to draw buildings and locations on the map
        buildingOverlay = createBuildingOverlay();
    }

    // You should not need to touch this method
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK)
            return;
    }

    // You should not need to touch this method
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        if (mapView == null) {
            mapView = new MapView(getActivity(), null);

            mapView.setTileSource(TileSourceFactory.MAPNIK);
            mapView.setClickable(true);
            mapView.setBuiltInZoomControls(true);
            mapView.setMultiTouchControls(true);

            mapController = mapView.getController();
            mapController.setZoom(mapView.getMaxZoomLevel() - 2);
            mapController.setCenter(UBC_MARTHA_PIPER_FOUNTAIN);
        }

        return mapView;
    }

    // You should not need to touch this method
    @Override
    public void onDestroyView() {
        Log.d(LOG_TAG, "onDestroyView");
        ((ViewGroup) mapView.getParent()).removeView(mapView);
        super.onDestroyView();
    }

    // You should not need to touch this method
    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    // You should not need to touch this method
    @Override
    public void onResume() {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
    }

    // You should not need to touch this method
    @Override
    public void onPause() {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
    }

    /**
     * Save map's zoom level and centre. You should not need to
     * touch this method
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(LOG_TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);

        if (mapView != null) {
            outState.putInt("zoomLevel", mapView.getZoomLevel());
            IGeoPoint cntr = mapView.getMapCenter();
            outState.putInt("latE6", cntr.getLatitudeE6());
            outState.putInt("lonE6", cntr.getLongitudeE6());
            Log.i("MapSave", "Zoom: " + mapView.getZoomLevel());
        }
    }

    // ****************** App Functionality

    /**
     * Show my schedule on the map. Every time "me"'s schedule shows, the map
     * should be cleared of all existing schedules, buildings, meetup locations, etc.
     */
    public void showMySchedule() {

       clearSchedules();
       String rightdays = sharedPreferences.getString("dayOfWeek", "MWF");
        Log.d("key1", rightdays);
       SortedSet<Section> aprsec = studentManager.get(ME_ID).getSchedule().getSections(rightdays);
       // Log.d("key1", String.valueOf(me.getSchedule()));
       // Log.d("key1", me.getLastName());
       String aprsecsize = String.valueOf(aprsec.size());
        Log.d("key1", aprsecsize);
       String fullname = me.getFirstName() + me.getLastName();
       SchedulePlot mySchedulePlot = new SchedulePlot(aprsec, fullname, "#FF0000", R.drawable.ic_action_place);

        // CPSC 210 Students: You must complete the implementation of this method.
        // The very last part of the method should call the asynchronous
        // task (which you will also write the code for) to plot the route
        // for "me"'s schedule for the day of the week set in the Settings

        // Asynchronous tasks are a bit onerous to deal with. In order to provide
        // all information needed in one object to plot "me"'s route, we
        // create a SchedulePlot object and pass it to the asynchrous task.
        // See the project page for more details.


        // Get a routing between these points. This line of code creates and calls
        // an asynchronous task to do the calls to MapQuest to determine a route
        // and plots the route.
        // Assumes mySchedulePlot is a create and initialized SchedulePlot object

        new GetRoutingForSchedule().execute(mySchedulePlot);
    }

    /**
     * Retrieve a random student's schedule from the Meetup web service and
     * plot a route for the schedule on the map. The plot should be for
     * the given day of the week as determined when "me"'s schedule
     * was plotted.
     */
    public void showRandomStudentsSchedule() {
        // To get a random student's schedule, we have to call the MeetUp web service.
        // Calling this web service requires a network access to we have to
        // do this in an asynchronous task. See below in this class for where
        // you need to implement methods for performing the network access
        // and plotting.


        new GetRandomSchedule().execute();
    }

    /**
     * Clear all schedules on the map
     */
    public void clearSchedules() {
        randomStudent = null;
        OverlayManager om = mapView.getOverlayManager();
        om.clear();
        scheduleOverlay.clear();
        buildingOverlay.removeAllItems();
        om.addAll(scheduleOverlay);
        om.add(buildingOverlay);
        mapView.invalidate();
    }

    /**
     * Find all possible locations at which "me" and random student could meet
     * up for the set day of the week and the set time to meet and the set
     * distance either "me" or random is willing to travel to meet.
     * A meetup is only possible if both "me" and random are free at the
     * time specified in the settings and each of us must have at least an hour
     * (>= 60 minutes) free. You should display dialog boxes if there are
     * conditions under which no meetup can happen (e.g., me or random is
     * in class at the specified time)
     */



    public void findMeetupPlace() {
        PlaceFactory pf = PlaceFactory.getInstance();
        // CPSC 210 students: you must complete this method
        Log.d("key10", String.valueOf(randomStudent.getId()));
        String dayoftheweek = sharedPreferences.getString("dayOfWeek", "MWF");
        String timeoftheday = sharedPreferences.getString("timeOfDay", "12");
        String snoopdistance = sharedPreferences.getString("placeDistance", "250");
        Log.d("key9", String.valueOf(snoopdistance));
        Set<String> starttimesme = studentManager.get(ME_ID).getSchedule().getStartTimesOfBreaks(dayoftheweek);
        Set<String> starttimerand = studentManager.get(randomStudent.getId()).getSchedule().getStartTimesOfBreaks(dayoftheweek);
        List<String> liststarttimesme = new ArrayList<String>(starttimesme);
        List<String> liststarttimesrand = new ArrayList<String>(starttimerand);
        Log.d("key6", String.valueOf(starttimesme));
        Log.d("key6", String.valueOf(starttimerand));
        boolean mebool = false;
        boolean randbool = false;
        boolean totalbool = false;
        boolean herro = false;
        boolean berro = false;
        Log.d("tag6", String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50");
        Log.d("key11", String.valueOf((liststarttimesme.get(0).compareTo(String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50") == 0)));
        CourseTime timeofbreakneeded = studentManager.get(ME_ID).getSchedule().startTime(dayoftheweek);
        try{
        timeofbreakneeded = new CourseTime((String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50"),
                (String.valueOf(Integer.parseInt(timeoftheday)) + ":50"));}
        catch(IllegalCourseTimeException e) {System.out.println("coursetime invalide!"); }

        if (studentManager.get(ME_ID).getSchedule().startTime(dayoftheweek).compareTo(timeofbreakneeded) > 0) {
            createSimpleDialog("Today's class hasn't started yet! Can't meet up!").show();
            //Log.d("key8", )
        }
        else if (studentManager.get(randomStudent.getId()).getSchedule().startTime(dayoftheweek).compareTo(timeofbreakneeded) > 0) {
            createSimpleDialog("Today's class hasn't started yet! Can't meet up!").show();
        }

        else {
            for (int i = 0; i < liststarttimesme.size(); i++) {
                if ((liststarttimesme.get(i).compareTo(String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50") == 0)) {
                    mebool = true;
                }
                else if ((studentManager.get(ME_ID).getSchedule().endTime(dayoftheweek).toString()).compareTo(
                        (String.valueOf(Integer.parseInt(timeoftheday)-2) + ":50")) <= 0) {
                    mebool = true;
                    herro = true;
                }

            }
            for (int j = 0; j < liststarttimesrand.size(); j++) {
                if ((liststarttimesrand.get(j).compareTo(String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50") == 0)) {
                    randbool = true;
                } else if ((studentManager.get(randomStudent.getId()).getSchedule().endTime(dayoftheweek).toString()).compareTo(
                        (String.valueOf(Integer.parseInt(timeoftheday)-2) + ":50")) <= 0) {
                    randbool = true;
                    berro = true;
                }
            }
            if (mebool == true && randbool == true) {
                totalbool = true;
            }
            Log.d("key7", String.valueOf(totalbool));
            Log.d("key7", String.valueOf(herro));
            Log.d("key7", String.valueOf(berro));

            List<Place> thelistofmeplaces = new ArrayList<Place>();
            List<Place> thelistofrandplaces = new ArrayList<Place>();
            List<Place> thelistofmutualplaces = new ArrayList<Place>();

            if (totalbool == true) {
                // at time what building is me in
                Building wheremeis = new Building("aa", new LatLon(UBC_MARTHA_PIPER_FOUNTAIN.getLatitude(),UBC_MARTHA_PIPER_FOUNTAIN.getLongitude()));
                Building whererandis = new Building("bb", new LatLon(UBC_MARTHA_PIPER_FOUNTAIN.getLatitude(), UBC_MARTHA_PIPER_FOUNTAIN.getLongitude()));
                if ((studentManager.get(ME_ID).getSchedule() == null) ||
                        (studentManager.get(randomStudent.getId()).getSchedule() == null)) {
                    createSimpleDialog("Schedule doesn't exist").show();
                } else {
                    if (herro == false) {
                        wheremeis = studentManager.get(ME_ID).getSchedule().whereAmI(dayoftheweek, String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50");
                        // from placefactory list of buildings find all buildings 100m within him
                    }

                    if (berro == false) {
                        whererandis = studentManager.get(randomStudent.getId()).getSchedule().whereAmI(dayoftheweek, String.valueOf(Integer.parseInt(timeoftheday) - 1) + ":50");
                    }

                    if (herro == true) {
                        wheremeis = studentManager.get(ME_ID).getSchedule().whereAmI(dayoftheweek,
                                studentManager.get(ME_ID).getSchedule().endTime(dayoftheweek).toString());
                    }
                    if (berro == true) {
                        whererandis = studentManager.get(randomStudent.getId()).getSchedule().whereAmI(dayoftheweek,
                                studentManager.get(randomStudent.getId()).getSchedule().endTime(dayoftheweek).toString());
                    }
                }
                LatLon latlonme = wheremeis.getLatLon(); //
                LatLon latlonrand = whererandis.getLatLon();
              //  Log.d("key7", String.valueOf(latlonme));
                // add to listof me buildings
                Set<Place> meplaces = pf.findPlacesWithinDistance(latlonme, Integer.parseInt(snoopdistance));
                thelistofmeplaces = new ArrayList<Place>(meplaces);

                //at time what building is rand in

                // from placefactory list of buildings find all buildings 100m within rand

               // Log.d("key7", String.valueOf(latlonrand));
                // add to list of rand buildings
                Set<Place> randplaces = pf.findPlacesWithinDistance(latlonrand, Integer.parseInt(snoopdistance));
                thelistofrandplaces = new ArrayList<Place>(randplaces);
                //find mutual buildings


                //if (studentManager.get(randomStudent.getId()).getSchedule() == null) {
                //   createSimpleDialog("Schedule doesn't exist").show();
                //    }
                /*
            else {
                    if (berro == false) {
                        whererandis = studentManager.get(randomStudent.getId()).getSchedule().whereAmI(dayoftheweek, timeoftheday);
                    } else {//if (berro == true) {
                        whererandis = studentManager.get(randomStudent.getId()).getSchedule().whereAmI(dayoftheweek,
                                studentManager.get(randomStudent.getId()).getSchedule().endTime(dayoftheweek).toString());
                    }
                }
                */


                for (int j = 0; j < thelistofmeplaces.size(); j++) {
                    for (int k = 0; k < thelistofrandplaces.size(); k++) {
                        if (thelistofmeplaces.get(j).getName().equals(thelistofrandplaces.get(k).getName())) {
                            thelistofmutualplaces.add(thelistofmeplaces.get(j));
                        }
                    }
                }

              //  Log.d("key13", String.valueOf(thelistofmutualplaces));

                //if (totalbool == false) {
                //    createSimpleDialog("No matching breaks! Sorry!").show();
                //} //else {
                plotBuildings3(thelistofmutualplaces);
                //}


            } else {
                createSimpleDialog("No matching breaks! Sorry!").show();
            }
        }
    }



    /**
     * Initialize the PlaceFactory with information from FourSquare
     */
    public void initializePlaces() {
        // CPSC 210 Students: You should not need to touch this method, but
        // you will have to implement GetPlaces below.
        new GetPlaces().execute();
    }


    /**
     * Plot all buildings referred to in the given information about plotting
     * a schedule.
     * @param schedulePlot All information about the schedule and route to plot.
     */
    private void plotBuildings(SchedulePlot schedulePlot) {

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        if (schedulePlot == null) {
            createSimpleDialog("Your schedulePlot is null!").show();
        }
        else {
            Set<Section> sectoplot = schedulePlot.getSections();
            List<Section> sectolist = new ArrayList<Section>(sectoplot);
            // sectolist.addAll(sectoplot);
            List<Building> listofbuildings = new ArrayList<Building>();
            for (int k = 0; k < sectolist.size(); k++) {
                listofbuildings.add(sectolist.get(k).getBuilding());
            }
            for (int i = 0; i < listofbuildings.size(); i++) {
                //String coursetm = "";
                if ((String.valueOf(sectolist.get(i).getCourseTime().getStartTime().charAt(1)) == ":"
                        && sectolist.get(i).getCourseTime().getStartTime().length() == 2)
                        ){
                    plotABuilding(listofbuildings.get(i), "Information Panel", me.getFirstName() +
                            ", you are at " + listofbuildings.get(i).getName()+"."+'\n'+sectolist.get(i).getCourse().getCode() +
                            String.valueOf(sectolist.get(i).getCourse().getNumber())+'\n'+"Section: " +sectolist.get(i).getName()+
                            '\n'+sectolist.get(i).getCourseTime().getStartTime()+"0"+" to "+ sectolist.get(i).getCourseTime().getEndTime(),
                            R.drawable.ic_action_place);
                }
                else if ((String.valueOf(sectolist.get(i).getCourseTime().getStartTime().charAt(2)) == ":"
                        && sectolist.get(i).getCourseTime().getStartTime().length() == 3)) {
                    plotABuilding(listofbuildings.get(i), "Information Panel", me.getFirstName() +
                            ", you are at " + listofbuildings.get(i).getName()+"."+'\n'+sectolist.get(i).getCourse().getCode() +
                            String.valueOf(sectolist.get(i).getCourse().getNumber())+'\n'+"Section: " +sectolist.get(i).getName()+
                            '\n'+sectolist.get(i).getCourseTime().getStartTime()+" to "+ sectolist.get(i).getCourseTime().getEndTime()+"0",
                            R.drawable.ic_action_place);
                }
                else {
                    plotABuilding(listofbuildings.get(i), "Information Panel", me.getFirstName() +
                            ", you are at " + listofbuildings.get(i).getName() +"."+ '\n' + sectolist.get(i).getCourse().getCode() +
                            String.valueOf(sectolist.get(i).getCourse().getNumber()) + '\n' + "Section: " +sectolist.get(i).getName() +
                            '\n' + sectolist.get(i).getCourseTime().getStartTime()+" to "+sectolist.get(i).getCourseTime().getEndTime(),
                            R.drawable.ic_action_place);
                }
            }

            // CPSC 210 Students: You will need to ensure the buildingOverlay is in
            // the overlayManager. The following code achieves this. You should not likely
            // need to touch it
            OverlayManager om = mapView.getOverlayManager();
            om.add(buildingOverlay);
        }
    }
//***********************************************newnewnewnewnewnewnew
    private void plotBuildings2(SchedulePlot schedulePlot) {

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
        if (schedulePlot == null) {
            createSimpleDialog("Your schedulePlot is null!").show();
        }
        else {
            Set<Section> sectoplot = schedulePlot.getSections();
            List<Section> sectolist = new ArrayList<Section>(sectoplot);
            // sectolist.addAll(sectoplot);
            List<Building> listofbuildings = new ArrayList<Building>();
            for (int k = 0; k < sectolist.size(); k++) {
                listofbuildings.add(sectolist.get(k).getBuilding());
            }
            for (int i = 0; i < listofbuildings.size(); i++) {
                //String coursetm = "";
                if ((String.valueOf(sectolist.get(i).getCourseTime().getStartTime().charAt(1)) == ":"
                        && sectolist.get(i).getCourseTime().getStartTime().length() == 2)
                        ){
                    plotABuilding(listofbuildings.get(i), "Information Panel", randomStudent.getFirstName() +
                                    ", you are at " + listofbuildings.get(i).getName()+"."+'\n'+sectolist.get(i).getCourse().getCode() +
                                    String.valueOf(sectolist.get(i).getCourse().getNumber())+'\n'+"Section: " +sectolist.get(i).getName()+
                                    '\n'+sectolist.get(i).getCourseTime().getStartTime()+"0"+" to "+ sectolist.get(i).getCourseTime().getEndTime(),
                            R.drawable.ic_action_place);
                }
                else if ((String.valueOf(sectolist.get(i).getCourseTime().getStartTime().charAt(2)) == ":"
                        && sectolist.get(i).getCourseTime().getStartTime().length() == 3)) {
                    plotABuilding(listofbuildings.get(i), "Information Panel", randomStudent.getFirstName() +
                                    ", you are at " + listofbuildings.get(i).getName()+"."+'\n'+sectolist.get(i).getCourse().getCode() +
                                    String.valueOf(sectolist.get(i).getCourse().getNumber())+'\n'+"Section: " +sectolist.get(i).getName()+
                                    '\n'+sectolist.get(i).getCourseTime().getStartTime()+" to "+ sectolist.get(i).getCourseTime().getEndTime()+"0",
                            R.drawable.ic_action_place);
                }
                else {
                    plotABuilding(listofbuildings.get(i), "Information Panel", randomStudent.getFirstName() +
                                    ", you are at " + listofbuildings.get(i).getName() +"."+ '\n' + sectolist.get(i).getCourse().getCode() +
                                    String.valueOf(sectolist.get(i).getCourse().getNumber()) + '\n' + "Section: " +sectolist.get(i).getName() +
                                    '\n' + sectolist.get(i).getCourseTime().getStartTime()+" to "+sectolist.get(i).getCourseTime().getEndTime(),
                            R.drawable.ic_action_place);
                }
            }

            // CPSC 210 Students: You will need to ensure the buildingOverlay is in
            // the overlayManager. The following code achieves this. You should not likely
            // need to touch it
            OverlayManager om = mapView.getOverlayManager();
            om.add(buildingOverlay);
        }
    }
    //************************************************NEWNEWNEWNEWNEW
    //********************************THIRDTHIRDTHIRD
    private void plotBuildings3(List<Place> listofplaces) {

        // CPSC 210 Students: Complete this method by plotting each building in the
        // schedulePlot with an appropriate message displayed
            for (int i = 0; i < listofplaces.size(); i++) {
                //String coursetm = "";

                plotABuilding(new Building(listofplaces.get(i).getName(), listofplaces.get(i).getLatLon()),
                        "Good place near both of you!", me.getFirstName() +
                    " and "+randomStudent.getFirstName()+", why not meet at"+'\n'+listofplaces.get(i).getName(),
                    R.drawable.ic_action_event);




            // CPSC 210 Students: You will need to ensure the buildingOverlay is in
            // the overlayManager. The following code achieves this. You should not likely
            // need to touch it
            OverlayManager om = mapView.getOverlayManager();
            om.add(buildingOverlay);
        }
    }
    //***********************************thirdthirdthird

    /**
     * Plot a building onto the map
     * @param building The building to put on the map
     * @param title The title to put in the dialog box when the building is tapped on the map
     * @param msg The message to display when the building is tapped
     * @param drawableToUse The icon to use. Can be R.drawable.ic_action_place (or any icon in the res/drawable directory)
     */
    private void plotABuilding(Building building, String title, String msg, int drawableToUse) {
        // CPSC 210 Students: You should not need to touch this method
        OverlayItem buildingItem = new OverlayItem(title, msg,
                new GeoPoint(building.getLatLon().getLatitude(), building.getLatLon().getLongitude()));

        //Create new marker
        Drawable icon = this.getResources().getDrawable(drawableToUse);

        //Set the bounding for the drawable
        icon.setBounds(
                0 - icon.getIntrinsicWidth() / 2, 0 - icon.getIntrinsicHeight(),
                icon.getIntrinsicWidth() / 2, 0);

        //Set the new marker to the overlay
        buildingItem.setMarker(icon);
        buildingOverlay.addItem(buildingItem);
    }



    /**
     * Initialize your schedule by coding it directly in. This is the schedule
     * that will appear on the map when you select "Show My Schedule".
     */
    private void initializeMySchedule() {
        // CPSC 210 Students; Implement this method
       // new GetMySchedule().execute();

       // Schedule mysched = new Schedule();

        studentManager = new StudentManager();
        me = new Student("S.", "Tim", ME_ID);

        studentManager.addStudent("S", "T", ME_ID);

        studentManager.addSectionToSchedule(ME_ID, "FREN", 102, "202");
        studentManager.addSectionToSchedule(ME_ID, "SCIE", 113, "213");
        studentManager.addSectionToSchedule(ME_ID, "MATH", 200, "201");

        //studentManager.addSectionToSchedule((ME_ID), "SCIE", 220, "200");
        studentManager.addSectionToSchedule(ME_ID, "PHYS", 203, "201");
        studentManager.addSectionToSchedule(ME_ID, "CPSC", 430, "201");





       // pick a few courses from the bottom and add them using studentmanager!
    }

    /**
     * Helper to create simple alert dialog to display message
     *
     * @param msg message to display in alert dialog
     * @return the alert dialog
     */
    private AlertDialog createSimpleDialog(String msg) {
        // CPSC 210 Students; You should not need to modify this method
        AlertDialog.Builder dialogBldr = new AlertDialog.Builder(getActivity());
        dialogBldr.setMessage(msg);
        dialogBldr.setNeutralButton(R.string.ok, null);

        return dialogBldr.create();
    }

    /**
     * Create the overlay used for buildings. CPSC 210 students, you should not need to
     * touch this method.
     * @return An overlay
     */
    private ItemizedIconOverlay<OverlayItem> createBuildingOverlay() {
        ResourceProxy rp = new DefaultResourceProxyImpl(getActivity());

        ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gestureListener =
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {

            /**
             * Display building description in dialog box when user taps stop.
             *
             * @param index
             *            index of item tapped
             * @param oi
             *            the OverlayItem that was tapped
             * @return true to indicate that tap event has been handled
             */
            @Override
            public boolean onItemSingleTapUp(int index, OverlayItem oi) {

                new AlertDialog.Builder(getActivity())
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                if (selectedBuildingOnMap != null) {
                                    mapView.invalidate();
                                }
                            }
                        }).setTitle(oi.getTitle()).setMessage(oi.getSnippet())
                        .show();

                selectedBuildingOnMap = oi;
                mapView.invalidate();
                return true;
            }

            @Override
            public boolean onItemLongPress(int index, OverlayItem oi) {
                // do nothing
                return false;
            }
        };

        return new ItemizedIconOverlay<OverlayItem>(
                new ArrayList<OverlayItem>(), getResources().getDrawable(
                R.drawable.ic_action_place), gestureListener, rp);
    }


    /**
     * Create overlay with a specific color
     * @param colour A string with a hex colour value
     */
    private PathOverlay createPathOverlay(String colour) {
        // CPSC 210 Students, you should not need to touch this method
        PathOverlay po = new PathOverlay(Color.parseColor(colour),
                getActivity());
        Paint pathPaint = new Paint();
        pathPaint.setColor(Color.parseColor(colour));
        pathPaint.setStrokeWidth(4.0f);
        pathPaint.setStyle(Paint.Style.STROKE);
        po.setPaint(pathPaint);
        return po;
    }

   // *********************** Asynchronous tasks

    /**
     * This asynchronous task is responsible for contacting the Meetup web service
     * for the schedule of a random student. The task must plot the retrieved
     * student's route for the schedule on the map in a different colour than the "me" schedule
     * or must display a dialog box that a schedule was not retrieved.
     */
    private class GetRandomSchedule extends AsyncTask<Void, Void, SchedulePlot> {

        // Some overview explanation of asynchronous tasks is on the project web page.

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(Void... params) {

            String jsonscriptv = "";
            int id = 000000;
            try {
                String urrl = "http://kramer.nss.cs.ubc.ca:8081/getStudent";
                jsonscriptv = makeRoutingCall(urrl);
            } catch (MalformedURLException e) {
                System.out.println("sorry!");
            } catch (IOException o) {
                System.out.println("sorry!");
            }
            try {
                JSONTokener tok = new JSONTokener(jsonscriptv);
                JSONObject job = new JSONObject(tok);
                String fn = job.getString("FirstName");

                id = Integer.parseInt(job.getString("Id"));
                String ln = job.getString("LastName");

                randomStudent = new Student(ln, fn, id);
                JSONArray sctns = job.getJSONArray("Sections");

                studentManager.addStudent(ln, fn, id);
               // Log.d("key2", studentManager.get(id).getLastName());
                Log.d("key3", String.valueOf(id));

                for (int i = 0; i < sctns.length(); i = i + 1) {
                    JSONObject sctn = sctns.getJSONObject(i);
                    String cname = "";
                    int cnumb = 0;
                    String secna = "";
                    cname = sctn.getString("CourseName");
                    cnumb = Integer.parseInt(sctn.getString("CourseNumber"));
                    secna = sctn.getString("SectionName");
                    studentManager.addSectionToSchedule(id, cname, cnumb, secna);
                }
                Log.d("key2", String.valueOf(studentManager.get(id).getSchedule().getSections("MWF").size()));

            } catch (JSONException e) {
                e.printStackTrace();
            }


          //  Log.d("key1", rightdays);
          // SchedulePlot randSchedulePlot = null;
            //Log.d("key2", studentManager.get(id).getLastName());
            SchedulePlot randSchedulePlot = null;
            String rightdays = sharedPreferences.getString("dayOfWeek", "MWF");
            if(studentManager.get(id).getSchedule().getSections(rightdays) != null) {
                SortedSet<Section> aprsec = studentManager.get(id).getSchedule().getSections(rightdays);
                String fullname = studentManager.get(id).getFirstName() + studentManager.get(id).getLastName();
                randSchedulePlot = new SchedulePlot(aprsec, fullname, "#800080", R.drawable.ic_action_place);
                // return randSchedulePlot;
            }
//**************************************
            Set<Section> sectoplot = randSchedulePlot.getSections();
            String sectoplotsize = String.valueOf(sectoplot);
            Log.d("key1", sectoplotsize);
            List<Section> sectolist = new ArrayList<Section>(sectoplot);
            String sectolistsize = String.valueOf(sectolist);
            Log.d("key1", sectolistsize);
            //  sectolist.addAll(sectoplot);
            List<Building> listofbuildings = new ArrayList<Building>();
            if (sectolist == null) {
                createSimpleDialog("Null Sections!").show();
            }
            else {
                for (int k = 0; k < sectolist.size(); k++) {
                    listofbuildings.add(sectolist.get(k).getBuilding());
                }
            }
            List<GeoPoint> thelist = new ArrayList<GeoPoint>();
            List<LatLon> latlonlist = new ArrayList<LatLon>();
            for (int j = 0; (j + 1) < listofbuildings.size(); j++) {

                Building bld1 = listofbuildings.get(j);
                Building bld2 = listofbuildings.get(j + 1);

                double lat1 = bld1.getLatLon().getLatitude();
                double lon1 = bld1.getLatLon().getLongitude();
                double lat2 = bld2.getLatLon().getLatitude();
                double lon2 = bld2.getLatLon().getLongitude();
                String jsonscript = "";
                try {
                    String url = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82l0znl%2Cax%3Do5-94zsdw&generalize=400&routeType=pedestrian&ambiguities=ignore&from=" + lat1 + "," + lon1 + "&to=" + lat2 + "," + lon2 + "&callback=renderNarrative";
                    jsonscript = makeRoutingCall(url);
                } catch (MalformedURLException e) {
                    System.out.println("sorry! malformed url exception");
                } catch (IOException o) {
                    System.out.println("sorry! ioexception");
                }
                try {
                    JSONTokener tok = new JSONTokener(jsonscript.replace("renderNarrative(",""));
                    JSONObject job = new JSONObject(tok);
                    JSONObject rte = job.getJSONObject("route");
                    JSONObject shp = rte.getJSONObject("shape");
                    JSONArray shppts = shp.getJSONArray("shapePoints");
                    for (int i = 0; (i + 1) < shppts.length(); i = i + 2) {
                        double lat = 0;
                        double lon = 0;
                        lat = shppts.getDouble(i);
                        lon = shppts.getDouble(i + 1);
                        LatLon gpi = new LatLon(lat, lon);
                        GeoPoint gpt = new GeoPoint(lat, lon);
                        latlonlist.add(gpi);
                        thelist.add(gpt);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                randSchedulePlot.setRoute(thelist);
            }
            return randSchedulePlot;


            //Log.d("key2", String.valueOf(randomStudent.getSchedule().getSections("TR").size()));
            // CPSC 210 Students: You must complete this method. It needs to
            // contact the Meetup web service to get a random student's schedule.
            // If it is successful in retrieving a student and their schedule,
            // it needs to remember the student in the randomStudent field
            // and it needs to create and return a schedulePlot object with
            // all relevant information for being ready to retrieve the route
            // and plot the route for the schedule. If no random student is
            // retrieved, return null.
            //
            // Note, leave all determination of routing and plotting until
            // the onPostExecute method below.
        	//	return randSchedulePlot;
        }

        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            // CPSC 210 students: When this method is called, it will be passed
            // whatever schedulePlot object you created (if any) in doBackground
            // above. Use it to plot the route.

            Set<Section> sectoplot = schedulePlot.getSections();
         //   String sectoplotsize = String.valueOf(sectoplot);
           // Log.d("key1", sectoplotsize);
            List<Section> sectolist = new ArrayList<Section>(sectoplot);

            if (schedulePlot == null) {
                createSimpleDialog("There is no route to plot!").show();
            }
            else {
               // Set<Section> sectoplot = schedulePlot.getSections();

              //  List<Section> sectolist = new ArrayList<Section>(sectoplot);
                // sectolist.addAll(sectoplot);
             //   List<Building> listofbuildings = new ArrayList<Building>();
                if (sectolist.size() == 0) {
                    createSimpleDialog("There is no route to plot!").show();
                }
                // CPSC 210 Students: This method should plot the route onto the map
                // with the given line colour specified in schedulePlot. If there is
                // no route to plot, a dialog box should be displayed.
                else if (schedulePlot.getRoute() == null) {
                    createSimpleDialog("There is no route to plot!").show();
                } else {

                    PathOverlay po = createPathOverlay("#800080");
                    plotBuildings2(schedulePlot);
                    for (int i = 0; i+1 < schedulePlot.getRoute().size(); i++) {


                        po.addPoint(schedulePlot.getRoute().get(i)); // one end of line
                        po.addPoint(schedulePlot.getRoute().get(i + 1)); // second end of line

                        //po.addPoint(UBC_MARTHA_PIPER_FOUNTAIN); // one end of line
                        //po.addPoint(new GeoPoint(49.262866, -123.25323));


                    }
                    scheduleOverlay.add(po);
                    OverlayManager om = mapView.getOverlayManager();
                    om.addAll(scheduleOverlay);
                    mapView.invalidate(); // cause map to redraw
                }


                plotBuildings(schedulePlot);


            }


        }
    }

    /**
     * This asynchronous task is responsible for contacting the MapQuest web service
     * to retrieve a route between the buildings on the schedule and for plotting any
     * determined route on the map.
     */
    private class GetRoutingForSchedule extends AsyncTask<SchedulePlot, Void, SchedulePlot> {

        @Override
        protected void onPreExecute() {
        }

        @Override
        protected SchedulePlot doInBackground(SchedulePlot... params) {
            // The params[0] element contains the schedulePlot object
            SchedulePlot scheduleToPlot = params[0];
            Set<Section> sectoplot = scheduleToPlot.getSections();
            String sectoplotsize = String.valueOf(sectoplot);
            Log.d("key1", sectoplotsize);
            List<Section> sectolist = new ArrayList<Section>(sectoplot);
            String sectolistsize = String.valueOf(sectolist);
            Log.d("key1", sectolistsize);
          //  sectolist.addAll(sectoplot);
            List<Building> listofbuildings = new ArrayList<Building>();
            if (sectolist == null) {
                createSimpleDialog("Null Sections!").show();
            }
            else {
                for (int k = 0; k < sectolist.size(); k++) {
                    listofbuildings.add(sectolist.get(k).getBuilding());
                }
            }
            //we need to get a list of latlons to compare them!
            //now we have the json file with routes, let's parse it:
            List<GeoPoint> thelist = new ArrayList<GeoPoint>();
            List<LatLon> latlonlist = new ArrayList<LatLon>();
        if (listofbuildings != null) {
            for (int j = 0; (j + 1) < listofbuildings.size(); j++) {

                Building bld1 = listofbuildings.get(j);
                Building bld2 = listofbuildings.get(j + 1);

                double lat1 = bld1.getLatLon().getLatitude();
                double lon1 = bld1.getLatLon().getLongitude();
                double lat2 = bld2.getLatLon().getLatitude();
                double lon2 = bld2.getLatLon().getLongitude();
                String jsonscript = "";
                try {
                    String url = "http://open.mapquestapi.com/directions/v2/route?key=Fmjtd%7Cluu82l0znl%2Cax%3Do5-94zsdw&generalize=400&routeType=pedestrian&ambiguities=ignore&from=" + lat1 + "," + lon1 + "&to=" + lat2 + "," + lon2 + "&callback=renderNarrative";
                    jsonscript = makeRoutingCall(url);
                } catch (MalformedURLException e) {
                    System.out.println("sorry! malformed url exception");
                } catch (IOException o) {
                    System.out.println("sorry! ioexception");
                }
                try {
                    JSONTokener tok = new JSONTokener(jsonscript.replace("renderNarrative(",""));
                    JSONObject job = new JSONObject(tok);
                    JSONObject rte = job.getJSONObject("route");
                    JSONObject shp = rte.getJSONObject("shape");
                    JSONArray shppts = shp.getJSONArray("shapePoints");
                    for (int i = 0; (i + 1) < shppts.length(); i = i + 2) {
                        double lat = 0;
                        double lon = 0;
                        lat = shppts.getDouble(i);
                        lon = shppts.getDouble(i + 1);
                        LatLon gpi = new LatLon(lat, lon);
                        GeoPoint gpt = new GeoPoint(lat, lon);
                        latlonlist.add(gpi);
                        thelist.add(gpt);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            String size = String.valueOf(thelist.size());

            scheduleToPlot.setRoute(thelist);
            Log.d("key1", "THE SIZE OF YOUR THING IS GOING TO BE THIS:" + size);
            return scheduleToPlot;
        }
            else {
                return null;
        }
    }


        /**
         * An example helper method to call a web service
         */
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }

        @Override
        protected void onPostExecute(SchedulePlot schedulePlot) {
            //Set<Section> sectoplot = null;

            if (schedulePlot == null) {
                createSimpleDialog("There is no route to plot!").show();
            }
            else {
                Set<Section> sectoplot = schedulePlot.getSections();

                List<Section> sectolist = new ArrayList<Section>(sectoplot);
                // sectolist.addAll(sectoplot);
                List<Building> listofbuildings = new ArrayList<Building>();
                if (sectolist.size() == 0) {
                    createSimpleDialog("There is no route to plot!").show();
                }
                // CPSC 210 Students: This method should plot the route onto the map
                // with the given line colour specified in schedulePlot. If there is
                // no route to plot, a dialog box should be displayed.
                else if (schedulePlot.getRoute().size() == 0) {
                    createSimpleDialog("There is no route to plot!").show();
                } else {

                    PathOverlay po = createPathOverlay("#FF0000");
                    plotBuildings(schedulePlot);
                    for (int i = 0; i+1 < schedulePlot.getRoute().size(); i++) {


                        po.addPoint(schedulePlot.getRoute().get(i)); // one end of line
                        po.addPoint(schedulePlot.getRoute().get(i + 1)); // second end of line

                        //po.addPoint(UBC_MARTHA_PIPER_FOUNTAIN); // one end of line
                        //po.addPoint(new GeoPoint(49.262866, -123.25323));


                    }
                    scheduleOverlay.add(po);
                    OverlayManager om = mapView.getOverlayManager();
                    om.addAll(scheduleOverlay);
                    mapView.invalidate(); // cause map to redraw
                }


                plotBuildings(schedulePlot);


            }
        }
    }

    /**
     * This asynchronous task is responsible for contacting the FourSquare web service
     * to retrieve all places around UBC that have to do with food. It should load
     * any determined places into PlaceFactory and then display a dialog box of how it did
     */
    private class GetPlaces extends AsyncTask<Void, Void, String> {

        protected String doInBackground(Void... params) {

            // CPSC 210 Students: Complete this method to retrieve a string
            // of JSON from FourSquare. Return the string from this method
            String soln = "";
            //String urlplace = "";
            String fod = sharedPreferences.getString("foodordrink", "food");
            try {
                double dalat = UBC_MARTHA_PIPER_FOUNTAIN.getLatitude();
                Log.d("key5", String.valueOf(dalat));
                double dalong = UBC_MARTHA_PIPER_FOUNTAIN.getLongitude();
                Log.d("key5", String.valueOf(dalong));

                 String urlplace = "https://api.foursquare.com/v2/venues/explore?ll=" + dalat + "," + dalong + "&radius=1000&" +
                            "section="+fod+"&limit=30&client_id=N0IGQLNOLBXBK3KJCAH1NIXA5NIPVLKNJI4SBATNKNXUWY1D&" +
                            "client_secret=13ZCYCZIUCZYVBZYU5ZREQXLNTBOW0XDFQKG3ETJHQEUNOKL&" +
                            "v=20150403";


                soln = makeRoutingCall(urlplace);
            } catch (MalformedURLException e) {
                System.out.println("sorry! malformed url exception");
            } catch (IOException o) {
                System.out.println("sorry! ioexception");
            }

            Log.d("key5", String.valueOf(soln.length()));
            return soln;

        }
        private String makeRoutingCall(String httpRequest) throws MalformedURLException, IOException {
            URL url = new URL(httpRequest);
            HttpURLConnection client = (HttpURLConnection) url.openConnection();
            InputStream in = client.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String returnString = br.readLine();
            client.disconnect();
            return returnString;
        }
        protected void onPostExecute(String jSONOfPlaces) {

            // CPSC 210 Students: Given JSON from FourQuest, parse it and load
            // PlaceFactory

            int iterator = 0;
            try {
                PlaceFactory pf = PlaceFactory.getInstance();
                JSONTokener tok = new JSONTokener(jSONOfPlaces);
                JSONObject job = new JSONObject(tok);
                JSONObject resp = job.getJSONObject("response");
                JSONArray gro = resp.getJSONArray("groups");
                JSONObject sbg = gro.getJSONObject(0);
                JSONArray items = sbg.getJSONArray("items");
                for (int i = 0; i < items.length(); i = i + 1) {
                    String name = "";
                    double lat = 0;
                    double lon = 0;
                    JSONObject item = items.getJSONObject(i);
                    JSONObject venue = item.getJSONObject("venue");
                    name = venue.getString("name");
                    JSONObject location = venue.getJSONObject("location");

                    lat = location.getDouble("lat");
                    lon = location.getDouble("lng");
                    LatLon gpi = new LatLon(lat, lon);
                  //  GeoPoint gpt = new GeoPoint(lat, lon);
                    EatingPlace ans = new EatingPlace(name, gpi);
                  //  latlonlist.add(gpi);
                  //  thelist.add(gpt);
                    pf.add(ans);
                    iterator++;
                    Log.d("key4", String.valueOf(iterator));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
            createSimpleDialog("You added " + String.valueOf(iterator) + " places!").show();

        }
    }

    /**
     * Initialize the CourseFactory with some courses.
     */
    private void initializeCourses() {
        // CPSC 210 Students: You can change this data if you desire.
        CourseFactory courseFactory = CourseFactory.getInstance();

        Building dmpBuilding = new Building("DMP", new LatLon(49.261474, -123.248060));

        Course cpsc210 = courseFactory.getCourse("CPSC", 210);
        Section aSection = new Section("202", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("201", "MWF", "16:00", "16:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);
        aSection = new Section("BCS", "MWF", "12:00", "12:50", dmpBuilding);
        cpsc210.addSection(aSection);
        aSection.setCourse(cpsc210);

        Course engl222 = courseFactory.getCourse("ENGL", 222);
        aSection = new Section("007", "MWF", "14:00", "14:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        engl222.addSection(aSection);
        aSection.setCourse(engl222);

        Course scie220 = courseFactory.getCourse("SCIE", 220);
        aSection = new Section("200", "MWF", "15:00", "15:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie220.addSection(aSection);
        aSection.setCourse(scie220);

        Course math200 = courseFactory.getCourse("MATH", 200);
        aSection = new Section("201", "MWF", "09:00", "09:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        math200.addSection(aSection);
        aSection.setCourse(math200);

        Course fren102 = courseFactory.getCourse("FREN", 102);
        aSection = new Section("202", "MWF", "11:00", "11:50", new Building("Barber", new LatLon(49.267442,-123.252471)));
        fren102.addSection(aSection);
        aSection.setCourse(fren102);

        Course japn103 = courseFactory.getCourse("JAPN", 103);
        aSection = new Section("002", "MWF", "10:00", "11:50", new Building("Buchanan", new LatLon(49.269258, -123.254784)));
        japn103.addSection(aSection);
        aSection.setCourse(japn103);

        Course scie113 = courseFactory.getCourse("SCIE", 113);
        aSection = new Section("213", "MWF", "13:00", "13:50", new Building("Swing", new LatLon(49.262786, -123.255044)));
        scie113.addSection(aSection);
        aSection.setCourse(scie113);

        Course micb308 = courseFactory.getCourse("MICB", 308);
        aSection = new Section("201", "MWF", "12:00", "12:50", new Building("Woodward", new LatLon(49.264704,-123.247536)));
        micb308.addSection(aSection);
        aSection.setCourse(micb308);

        Course math221 = courseFactory.getCourse("MATH", 221);
        aSection = new Section("202", "TR", "11:00", "12:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        math221.addSection(aSection);
        aSection.setCourse(math221);

        Course phys203 = courseFactory.getCourse("PHYS", 203);
        aSection = new Section("201", "TR", "09:30", "10:50", new Building("Hennings", new LatLon(49.266400,-123.252047)));
        phys203.addSection(aSection);
        aSection.setCourse(phys203);

        Course crwr209 = courseFactory.getCourse("CRWR", 209);
        aSection = new Section("002", "TR", "12:30", "13:50", new Building("Geography", new LatLon(49.266039,-123.256129)));
        crwr209.addSection(aSection);
        aSection.setCourse(crwr209);

        Course fnh330 = courseFactory.getCourse("FNH", 330);
        aSection = new Section("002", "TR", "15:00", "16:20", new Building("MacMillian", new LatLon(49.261167,-123.251157)));
        fnh330.addSection(aSection);
        aSection.setCourse(fnh330);

        Course cpsc499 = courseFactory.getCourse("CPSC", 430);
        aSection = new Section("201", "TR", "16:20", "17:50", new Building("Liu", new LatLon(49.267632,-123.259334)));
        cpsc499.addSection(aSection);
        aSection.setCourse(cpsc499);

        Course chem250 = courseFactory.getCourse("CHEM", 250);
        aSection = new Section("203", "TR", "10:00", "11:20", new Building("Klinck", new LatLon(49.266112, -123.254776)));
        chem250.addSection(aSection);
        aSection.setCourse(chem250);

        Course eosc222 = courseFactory.getCourse("EOSC", 222);
        aSection = new Section("200", "TR", "11:00", "12:20", new Building("ESB", new LatLon(49.262866, -123.25323)));
        eosc222.addSection(aSection);
        aSection.setCourse(eosc222);

        Course biol201 = courseFactory.getCourse("BIOL", 201);
        aSection = new Section("201", "TR", "14:00", "15:20", new Building("BioSci", new LatLon(49.263920, -123.251552)));
        biol201.addSection(aSection);
        aSection.setCourse(biol201);
    }

}
