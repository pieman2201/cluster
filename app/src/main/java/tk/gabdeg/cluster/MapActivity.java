package tk.gabdeg.cluster;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.transition.ChangeBounds;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.cocoahero.android.geojson.FeatureCollection;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.layers.TransitionOptions;
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements MapboxMap.OnMapClickListener {

    private MapView mapView;
    private MapboxMap mapboxMap;
    private ProgressBar spinner;
    private boolean finishedLoading = false;
    private GeoJsonSource source;
    private RefreshPostsTask refreshPostsTask;

    void checkIfLoaded() {
        if (finishedLoading) {
            mapView.setVisibility(View.VISIBLE);
            spinner.setVisibility(View.GONE);
        }
    }

    @SuppressLint("MissingPermission")
    void enableLocationComponent(Style mapboxStyle) {
        Log.d("location component", "enabling!");
        if (mapboxMap != null) {
            LocationComponent locationComponent = mapboxMap.getLocationComponent();
            LocationComponentActivationOptions locationComponentActivationOptions = LocationComponentActivationOptions.builder(this, mapboxStyle)
                    .locationComponentOptions(
                            LocationComponentOptions.builder(this)
                                    //.layerAbove("points-clustered")
                                    .accuracyAlpha(0)
                                    .elevation(0)
                                    .backgroundTintColor(getResources().getColor(R.color.primaryTextColor))
                                    .backgroundStaleTintColor(getResources().getColor(R.color.primaryTextColor))
                                    .foregroundTintColor(getResources().getColor(R.color.locationColor))
                                    .foregroundStaleTintColor(getResources().getColor(R.color.locationColor))
                                    .build()
                    )
                    .useDefaultLocationEngine(true)
                    .build();
            locationComponent.activateLocationComponent(locationComponentActivationOptions);
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setRenderMode(RenderMode.NORMAL);
        }
    }

    int mixTwoColors(int color1, int color2, float amount) {
        final byte ALPHA_CHANNEL = 24;
        final byte RED_CHANNEL = 16;
        final byte GREEN_CHANNEL = 8;
        final byte BLUE_CHANNEL = 0;

        final float inverseAmount = 1.0f - amount;

        int a = ((int) (((float) (color1 >> ALPHA_CHANNEL & 0xff) * amount) +
                ((float) (color2 >> ALPHA_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int r = ((int) (((float) (color1 >> RED_CHANNEL & 0xff) * amount) +
                ((float) (color2 >> RED_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int g = ((int) (((float) (color1 >> GREEN_CHANNEL & 0xff) * amount) +
                ((float) (color2 >> GREEN_CHANNEL & 0xff) * inverseAmount))) & 0xff;
        int b = ((int) (((float) (color1 & 0xff) * amount) +
                ((float) (color2 & 0xff) * inverseAmount))) & 0xff;

        return a << ALPHA_CHANNEL | r << RED_CHANNEL | g << GREEN_CHANNEL | b << BLUE_CHANNEL;
    }

    String mixColorsHex(int color1, int color2, double amount) {
        int mixed = 0xFF000000 + mixTwoColors(color1, color2, (float) amount);
        return String.format("#%06X", (0xFFFFFF & mixed));
    }

    int getNavBarHeight() {
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    int getStatusBarHeight() {
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    LatLng getLatLng(Location loc) {
        return new LatLng(loc.getLatitude(), loc.getLongitude());
    }

    void onUserFirstLocated() {
        if (mapboxMap != null) {
            CameraPosition position = new CameraPosition.Builder().target(getLatLng(mapboxMap.getLocationComponent().getLastKnownLocation())).zoom(13.0).build();
            mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(position));
            checkIfLoaded();
        }
    }

    void jumpToUserLocation() {
        if (mapboxMap != null && mapView != null) {
            CameraPosition position = new CameraPosition.Builder().target(getLatLng(mapboxMap.getLocationComponent().getLastKnownLocation())).zoom(Math.max(mapboxMap.getCameraPosition().zoom, 13.0)).build();
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position), 1000);
        }
    }

    void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);
            locationClient.getLastLocation().addOnSuccessListener(this, loc -> {
                if (loc != null) {
                    new GetPostsTask().execute(getLatLng(loc));
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 1) {
            checkLocationPermission();
        }
    }

    void clickCluster(List<Post> posts) {
        Log.d("cluster clicked", posts.size() + " children");
    }

    void openPopup() {
        ConstraintSet popup = new ConstraintSet();
        popup.clone((ConstraintLayout) findViewById(R.id.layout));

        Point dimensions = new Point();
        getWindowManager().getDefaultDisplay().getSize(dimensions);

        popup.setGuidelinePercent(R.id.infoFrameExtent, 0.5f);
        popup.setVisibility(R.id.infoFrame, ConstraintSet.VISIBLE);
        popup.setVisibility(R.id.locateFab, ConstraintSet.GONE);
        popup.setVisibility(R.id.postFab, ConstraintSet.GONE);

        Transition overshoot = new ChangeBounds();
        overshoot.setInterpolator(new DecelerateInterpolator());

        TransitionManager.beginDelayedTransition(findViewById(R.id.layout), findViewById(R.id.infoFrame).getVisibility() == View.VISIBLE ? overshoot : new Slide());
        popup.applyTo(findViewById(R.id.layout));
    }

    void openExpanded() {
        ConstraintSet expanded = new ConstraintSet();
        expanded.clone((ConstraintLayout) findViewById(R.id.layout));

        Point dimensions = new Point();
        getWindowManager().getDefaultDisplay().getSize(dimensions);

        expanded.setGuidelinePercent(R.id.infoFrameExtent, 0f);
        expanded.setVisibility(R.id.infoFrame, ConstraintSet.VISIBLE);
        expanded.setVisibility(R.id.locateFab, ConstraintSet.GONE);
        expanded.setVisibility(R.id.postFab, ConstraintSet.GONE);

        Transition overshoot = new ChangeBounds();
        overshoot.setInterpolator(new DecelerateInterpolator());

        TransitionManager.beginDelayedTransition(findViewById(R.id.layout), overshoot);
        expanded.applyTo(findViewById(R.id.layout));
    }

    void closeOverlay() {
        ConstraintSet closed = new ConstraintSet();
        closed.clone((ConstraintLayout) findViewById(R.id.layout));

        closed.setGuidelinePercent(R.id.infoFrameExtent, 1);
        closed.setVisibility(R.id.infoFrame, ConstraintSet.GONE);
        closed.setVisibility(R.id.locateFab, ConstraintSet.VISIBLE);
        closed.setVisibility(R.id.postFab, ConstraintSet.VISIBLE);

        Transition transition = new Slide();
        transition.addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionEnd(Transition transition) {
                if (getSupportFragmentManager().findFragmentById(R.id.infoFrame) != null) {
                    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                    fragmentTransaction.remove(getSupportFragmentManager().findFragmentById(R.id.infoFrame));
                    fragmentTransaction.commit();
                }
            }

            @Override
            public void onTransitionCancel(Transition transition) {
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }

            @Override
            public void onTransitionStart(Transition transition) {
            }
        });

        TransitionManager.beginDelayedTransition(findViewById(R.id.layout), transition);
        closed.applyTo(findViewById(R.id.layout));
    }

    void toggleInfoFragmentSize() {
        Guideline guideline = findViewById(R.id.infoFrameExtent);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) guideline.getLayoutParams();
        if (params.guidePercent == 0.5f) { openExpanded(); } else { openPopup(); }
    }

    void removeInfoFragment() {
        closeOverlay();
    }

    void clickPost(Post post) {
        if (getSupportFragmentManager().findFragmentById(R.id.infoFrame) != null) {
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.remove(getSupportFragmentManager().findFragmentById(R.id.infoFrame));
            fragmentTransaction.commit();
        }
        Log.d("post clicked", "id=" + post.id);
        if (mapboxMap != null) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            Bundle bundle = new Bundle();
            bundle.putString(InfoFragment.POST_KEY, new Gson().toJson(post));
            InfoFragment infoFragment = new InfoFragment();
            infoFragment.setArguments(bundle);
            fragmentTransaction.replace(R.id.infoFrame, infoFragment);
            fragmentTransaction.commit();
            fragmentManager.executePendingTransactions();

            openPopup();

            LatLngBounds currentBounds = mapboxMap.getProjection().getVisibleRegion().latLngBounds;
            CameraUpdate moveToPost = CameraUpdateFactory.newLatLng(new LatLng(post.latitude - currentBounds.getLatitudeSpan() / 4, post.longitude));
            mapboxMap.easeCamera(moveToPost, 750, true);
        }
    }

    public boolean onMapClick(@NonNull LatLng point) {
        PointF pointf = mapboxMap.getProjection().toScreenLocation(point);
        List<com.mapbox.geojson.Feature> featureList = mapboxMap.queryRenderedFeatures(pointf, "points-clustered");
        if (featureList.size() > 0) {
            for (com.mapbox.geojson.Feature feature : featureList) {
                Log.d("points", feature.toJson());
                if (source.getClusterExpansionZoom(feature) <= mapboxMap.getMaxZoomLevel()) {
                    try {
                        assert feature.geometry() != null;
                        LatLng pos = new LatLng(
                                new JSONObject(feature.geometry().toJson()).getJSONArray("coordinates").getDouble(1),
                                new JSONObject(feature.geometry().toJson()).getJSONArray("coordinates").getDouble(0)
                        );
                        Log.d("points", pos.toString());
                        CameraPosition position = new CameraPosition.Builder()
                                .target(pos)
                                .zoom(source.getClusterExpansionZoom(feature) + 0.1)
                                .build();
                        mapboxMap.animateCamera(
                                CameraUpdateFactory.newCameraPosition(position),
                                750
                        );

                        removeInfoFragment();
                    } catch (JSONException e) {
                        continue;
                    }
                } else {
                    List<Post> children = new ArrayList<>();
                    for (com.mapbox.geojson.Feature child : source.getClusterLeaves(feature, feature.getNumberProperty("point_count").longValue(), 0).features()) {
                        children.add(new Gson().fromJson(child.properties().toString(), Post.class));
                    }
                    MapActivity.this.clickCluster(children);
                }
                Log.d("points", "zoom level " + source.getClusterExpansionZoom(feature));
            }
            return true;
        }
        featureList = mapboxMap.queryRenderedFeatures(pointf, "points");
        if (featureList.size() > 0) {
            for (com.mapbox.geojson.Feature feature : featureList) {
                Log.d("points", new Gson().fromJson(feature.properties().toString(), Post.class).toString());
                MapActivity.this.clickPost(new Gson().fromJson(feature.properties().toString(), Post.class));
            }
            return true;
        }
        return false;
    }

    void loadMap(FeatureCollection posts) {
        if (mapView != null) {
            mapView.getMapAsync(map -> {

                mapboxMap = map;
                mapboxMap.getUiSettings().setRotateGesturesEnabled(false);
                mapboxMap.getUiSettings().setTiltGesturesEnabled(false);

                mapboxMap.setStyle(Style.DARK, style -> {
                    String jsonStr = "{\"type\": \"FeatureCollection\", \"features\": []}";
                    if (posts != null) {
                        try {
                            Log.d("points", "adding source");
                            jsonStr = posts.toJSON().toString();
                        } catch (JSONException e) {
                            Log.d("points", "bad json!");
                        }
                    }
                    source = new GeoJsonSource("points", jsonStr, new GeoJsonOptions().withCluster(true).withClusterMaxZoom(26).withMaxZoom(26).withClusterRadius(48));
                    style.addSource(source);

                    float defaultRadius = 10f;
                    int defaultColor = getResources().getColor(R.color.unclusteredColor);
                    int clusteredColor = getResources().getColor(R.color.clusteredColor);

                    Log.d("points", "adding base layer");
                    style.addLayer(new CircleLayer("points", "points").withProperties(PropertyFactory.circleColor(defaultColor), PropertyFactory.circleRadius(defaultRadius + 2f))
                            .withFilter(Expression.not(Expression.has("point_count")))
                    );
                    Log.d("points", "adding cluster layer");
                    CircleLayer clusterLayer = new CircleLayer("points-clustered", "points").withProperties(
                            PropertyFactory.circleColor(Expression.step(
                                    Expression.get("point_count"),
                                    Expression.literal(mixColorsHex(defaultColor, clusteredColor, 0.75)),
                                    Expression.stop(10, mixColorsHex(defaultColor, clusteredColor, 0.5)),
                                    Expression.stop(100, mixColorsHex(defaultColor, clusteredColor, 0.25)),
                                    Expression.stop(1000, mixColorsHex(defaultColor, clusteredColor, 0)))),
                            PropertyFactory.circleRadius(Expression.product(Expression.sum(Expression.literal(1.5), Expression.division(Expression.log10(Expression.get("point_count")), Expression.literal(2))), Expression.literal(defaultRadius))),
                            PropertyFactory.circleStrokeWidth(4f),
                            PropertyFactory.circleStrokeColor(Color.WHITE)
                    );
                    clusterLayer.setFilter(Expression.has("point_count"));
                    style.addLayer(clusterLayer);
                    SymbolLayer textLayer = new SymbolLayer("point_labels", "points").withProperties(
                            PropertyFactory.textAllowOverlap(true),
                            PropertyFactory.textColor(Color.BLACK),
                            PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                            PropertyFactory.textIgnorePlacement(false),
                            PropertyFactory.textSize(14f)
                    );
                    style.setTransition(new TransitionOptions(0, 0, false));
                    style.addLayer(textLayer);

                    enableLocationComponent(style);
                    mapboxMap.addOnMapClickListener(this);
                });
            });
        }
    }

    void refreshMap(FeatureCollection posts) {

        if (source != null && posts != null) {
            String jsonStr = "";
            try {
                Log.d("points-refresh", "adding source");
                jsonStr = posts.toJSON().toString();
            } catch (JSONException e) {
                Log.d("points-refresh", "bad json!");
            }
            source.setGeoJson(jsonStr);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(R.style.AppTheme);

        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, APIKey.key);
        setContentView(R.layout.activity_main);
        ((NavigationView) findViewById(R.id.navigation_drawer)).setNavigationItemSelectedListener(menuItem -> {
            ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(Gravity.LEFT);
            switch (menuItem.getItemId()) {
                case R.id.menu_profile:
                    Intent intent = new Intent(getApplicationContext(), ProfileActivity.class);
                    startActivity(intent);
                    break;
                default:
                    break;
            }
            return true;
        });

        findViewById(R.id.navigation_button).setOnClickListener(v -> ((DrawerLayout) findViewById(R.id.drawer_layout)).openDrawer(Gravity.LEFT));
        ((NavigationView) findViewById(R.id.navigation_drawer)).getHeaderView(0).setPadding(0, getStatusBarHeight(), 0, 0);

        mapView = findViewById(R.id.mapView);
        FloatingActionButton postFab = findViewById(R.id.postFab);
        spinner = findViewById(R.id.spinner);
        postFab.setOnClickListener(v -> {
            Log.d("post", "posted!");
            if (mapboxMap.getLocationComponent().getLastKnownLocation() != null) {
                Location location = mapboxMap.getLocationComponent().getLastKnownLocation();
                Intent intent = new Intent(this, SubmitActivity.class);

                Post put = new Post();
                put.latitude = location.getLatitude();
                put.longitude = location.getLongitude();

                intent.putExtra(SubmitActivity.LOCATION_KEY, new Gson().toJson(put));
                Log.d("post", getLatLng(location).toString());
                startActivity(intent);
            }
        });
        FloatingActionButton locateFab = findViewById(R.id.locateFab);
        locateFab.setOnClickListener(v -> jumpToUserLocation());

        mapView.onCreate(savedInstanceState);
        mapView.addOnDidFinishRenderingMapListener(fully -> {
            Log.d("style", "finished rendering");
            finishedLoading = true;
            refreshPostsTask = new RefreshPostsTask(false);
            refreshPostsTask.execute(getLatLng(mapboxMap.getLocationComponent().getLastKnownLocation()));
            onUserFirstLocated();
        });

        checkLocationPermission();
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
        if (refreshPostsTask != null) {
            refreshPostsTask.cancel(true);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private class GetPostsTask extends AsyncTask<LatLng, Void, FeatureCollection> {
        private LatLng loc;

        protected FeatureCollection doInBackground(LatLng... positions) {
            loc = positions[0];
            return new Backend().getPosts(positions[0]);
        }

        @Override
        protected void onPostExecute(FeatureCollection featureCollection) {
            if (featureCollection != null) {
                loadMap(featureCollection);
            } else {
                new GetPostsTask().execute(loc);
            }
        }
    }

    private class RefreshPostsTask extends AsyncTask<LatLng, Void, FeatureCollection> {
        private boolean oneOff = false;
        private LatLng loc;

        public RefreshPostsTask(boolean isOneOff) {
            oneOff = isOneOff;
        }

        protected FeatureCollection doInBackground(LatLng... positions) {
            Log.d("refresh", "refreshing!");
            loc = positions[0];
            return new Backend().getPosts(positions[0]);
        }

        @Override
        protected void onPostExecute(FeatureCollection featureCollection) {
            if (isCancelled()) {
                return;
            }
            refreshMap(featureCollection);
            if (!oneOff) {
                new Handler().postDelayed(() -> {
                    refreshPostsTask = new RefreshPostsTask(false);
                    refreshPostsTask.execute(getLatLng(mapboxMap.getLocationComponent().getLastKnownLocation()));
                }, (featureCollection != null ? 30 : 5) * 1000);
            }
        }
    }
}