/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2016 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplibui.activity;

import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nextgis.maplib.api.GpsEventListener;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoMultiPoint;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.location.AccurateLocationTaker;
import com.nextgis.maplib.location.GpsEventSource;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.LocationUtil;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.api.IControl;
import com.nextgis.maplibui.api.IFormControl;
import com.nextgis.maplibui.api.ISimpleControl;
import com.nextgis.maplibui.control.DateTime;
import com.nextgis.maplibui.control.PhotoGallery;
import com.nextgis.maplibui.control.TextEdit;
import com.nextgis.maplibui.control.TextLabel;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.NotificationHelper;
import com.nextgis.maplibui.util.SettingsConstantsUI;

import org.json.JSONException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nextgis.maplib.util.Constants.FIELD_GEOM;
import static com.nextgis.maplib.util.Constants.FIELD_ID;
import static com.nextgis.maplib.util.Constants.NOT_FOUND;
import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_FEATURE_ID;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_GEOMETRY;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_GEOMETRY_CHANGED;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_LAYER_ID;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_VIEW_ONLY;


/**
 * Activity to add or modify vector layer attributes
 */
public class ModifyAttributesActivity
        extends NGActivity
        implements GpsEventListener
{
    protected long PROGRESS_DELAY = 1000L;
    protected long MAX_TAKE_TIME  = Integer.MAX_VALUE;

    protected Map<String, IControl> mFields;
    protected VectorLayer           mLayer;
    protected long                  mFeatureId;

    protected GeoGeometry           mGeometry;
    protected TextView              mLatView;
    protected TextView              mLongView;
    protected TextView              mAltView;
    protected TextView              mAccView;
    protected SwitchCompat          mAccurateLocation;
    protected AppCompatSpinner      mAccuracyCE;

    protected Location              mLocation;
    protected SharedPreferences mSharedPreferences;

    protected int mMaxTakeCount;
    protected boolean mIsGeometryChanged;
    protected boolean mIsViewOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_standard_attributes);
        setToolbar(R.id.main_toolbar);

        final IGISApplication app = (IGISApplication) getApplication();
        createView(app, savedInstanceState);
        createLocationPanelView(app);
    }

    protected void createLocationPanelView(final IGISApplication app)
    {
        if (null == mGeometry && mFeatureId == NOT_FOUND) {
            mLatView = (TextView) findViewById(R.id.latitude_view);
            mLongView = (TextView) findViewById(R.id.longitude_view);
            mAltView = (TextView) findViewById(R.id.altitude_view);
            mAccView = (TextView) findViewById(R.id.accuracy_view);
            mAccurateLocation = (SwitchCompat) findViewById(R.id.accurate_location);
            mAccuracyCE = (AppCompatSpinner) findViewById(R.id.accurate_ce);

            final ImageButton refreshLocation = (ImageButton) findViewById(R.id.refresh);
            refreshLocation.setOnClickListener(
                    new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View view)
                        {
                            RotateAnimation rotateAnimation = new RotateAnimation(
                                    0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                                    Animation.RELATIVE_TO_SELF, 0.5f);
                            rotateAnimation.setDuration(700);
                            rotateAnimation.setRepeatCount(0);
                            refreshLocation.startAnimation(rotateAnimation);

                            if (mAccurateLocation.isChecked()) {
                                final ProgressDialog progress;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                    progress = new ProgressDialog(view.getContext(), android.R.style.Theme_Material_Light_Dialog_Alert);
                                else
                                    progress = new ProgressDialog(view.getContext());

                                final AccurateLocationTaker accurateLocation =
                                        new AccurateLocationTaker(view.getContext(), 100f,
                                                mMaxTakeCount, MAX_TAKE_TIME, PROGRESS_DELAY, (String) mAccuracyCE.getSelectedItem());

                                progress.setMax(mMaxTakeCount);
                                progress.setCanceledOnTouchOutside(false);
                                progress.setMessage(getString(R.string.accurate_taking));
                                progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                                progress.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        accurateLocation.cancelTaking();
                                    }
                                });

                                accurateLocation.setOnProgressUpdateListener(new AccurateLocationTaker.OnProgressUpdateListener() {
                                    @Override
                                    public void onProgressUpdate(Long... values) {
                                        progress.setProgress(values[0].intValue());
                                    }
                                });

                                accurateLocation.setOnGetAccurateLocationListener(new AccurateLocationTaker.OnGetAccurateLocationListener() {
                                    @Override
                                    public void onGetAccurateLocation(Location accurateLocation, Long... values) {
                                        progress.dismiss();
                                        setLocationText(accurateLocation);
                                    }
                                });

                                progress.show();
                                accurateLocation.startTaking();
                            } else if (null != app) {
                                GpsEventSource gpsEventSource = app.getGpsEventSource();
                                Location location = gpsEventSource.getLastKnownLocation();
                                setLocationText(location);
                            }
                        }
                    });
        } else {
            //hide location panel
            ViewGroup rootView = (ViewGroup) findViewById(R.id.root_view);
            rootView.removeView(findViewById(R.id.location_panel));
        }
    }


    protected void createView(final IGISApplication app, Bundle savedState)
    {
        //create and fill controls
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            int layerId = extras.getInt(KEY_LAYER_ID);
            MapBase map = app.getMap();
            mLayer = (VectorLayer) map.getLayerById(layerId);

            if (null != mLayer) {
                mSharedPreferences = mLayer.getPreferences();

                mFields = new HashMap<>();
                mFeatureId = extras.getLong(KEY_FEATURE_ID);
                mIsViewOnly = extras.getBoolean(KEY_VIEW_ONLY, false);
                mIsGeometryChanged = extras.getBoolean(KEY_GEOMETRY_CHANGED, true);
                mGeometry = (GeoGeometry) extras.getSerializable(KEY_GEOMETRY);
                LinearLayout layout = (LinearLayout) findViewById(R.id.controls_list);
                fillControls(layout, savedState);
            } else {
                Toast.makeText(this, R.string.error_layer_not_inited, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    protected void fillControls(LinearLayout layout, Bundle savedState)
    {
        Cursor featureCursor = null;
        if (mFeatureId != NOT_FOUND) {
            featureCursor = mLayer.query(null, FIELD_ID + " = " + mFeatureId, null, null, null);
            if (!featureCursor.moveToFirst()) {
                featureCursor = null;
            }
        }

        List<Field> fields = mLayer.getFields();
        for (Field field : fields) {
            //create static text with alias
            TextLabel textLabel = (TextLabel)getLayoutInflater().inflate(R.layout.template_textlabel, layout, false);
            textLabel.setText(field.getAlias());
            textLabel.addToLayout(layout);

            ISimpleControl control = null;

            //create control
            switch (field.getType()) {

                case GeoConstants.FTString:
                case GeoConstants.FTInteger:
                case GeoConstants.FTReal:
                    TextEdit textEdit = (TextEdit) getLayoutInflater().inflate(R.layout.template_textedit, layout, false);
                    if (mIsViewOnly) {
                        textEdit.setEnabled(false);
                    }
                    control = textEdit;
                    break;
                case GeoConstants.FTDate:
                case GeoConstants.FTTime:
                case GeoConstants.FTDateTime:
                    DateTime dateTime = (DateTime) getLayoutInflater().inflate(R.layout.template_datetime, layout, false);
                    if (mIsViewOnly) {
                        dateTime.setEnabled(false);
                    }
                    control = dateTime;
                    break;
                case GeoConstants.FTBinary:
                case GeoConstants.FTStringList:
                case GeoConstants.FTIntegerList:
                case GeoConstants.FTRealList:
                    //TODO: add support for this types
                    break;

                default:
                    break;
            }

            if (null != control) {
                control.init(field, savedState, featureCursor);
                control.addToLayout(layout);
                String fieldName = control.getFieldName();

                if (null != fieldName) {
                    mFields.put(fieldName, control);
                }
            }
        }

        try {
            if (!mIsViewOnly) {
                IFormControl control = (PhotoGallery) getLayoutInflater().inflate(
                        R.layout.formtemplate_photo, layout, false);
                ((PhotoGallery) control).init(mLayer, mFeatureId);
                control.init(null, null, null, null, null);
                control.addToLayout(layout);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (null != featureCursor) {
            featureCursor.close();
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        LinearLayout controlLayout = (LinearLayout) findViewById(R.id.controls_list);
        for (int i = 0; i < controlLayout.getChildCount(); i++)
            if (controlLayout.getChildAt(i) instanceof IControl)
                ((IControl) controlLayout.getChildAt(i)).saveState(outState);
    }


    @Override
    protected void onPause()
    {
        if (null != findViewById(R.id.location_panel)) {
            IGISApplication app = (IGISApplication) getApplication();
            if (null != app) {
                GpsEventSource gpsEventSource = app.getGpsEventSource();
                gpsEventSource.removeListener(this);
            }
        }
        super.onPause();
    }


    @Override
    protected void onResume()
    {
        if (null != findViewById(R.id.location_panel)) {
            IGISApplication app = (IGISApplication) getApplication();
            if (null != app) {
                GpsEventSource gpsEventSource = app.getGpsEventSource();
                gpsEventSource.addListener(this);
                NotificationHelper.showLocationInfo(this);
                setLocationText(gpsEventSource.getLastKnownLocation());
            }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            mMaxTakeCount = Integer.parseInt(prefs.getString(SettingsConstants.KEY_PREF_LOCATION_ACCURATE_COUNT, "20"));
        }
        super.onResume();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.edit_attributes, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish(); // TODO prompt dialog on unsaved data
            return true;
        } else if (id == R.id.menu_settings) {
            final IGISApplication app = (IGISApplication) getApplication();
            app.showSettings(SettingsConstantsUI.ACTION_PREFS_GENERAL);
            return true;
        } else if (id == R.id.menu_apply) {
            saveFeature();
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        PhotoGallery gallery = (PhotoGallery) findViewById(R.id.pg_photos);
        if (gallery != null)
            gallery.onActivityResult(requestCode, resultCode, data);
    }


    protected void saveFeature()
    {
        //create new row or modify existing
        List<Field> fields = mLayer.getFields();
        ContentValues values = new ContentValues();

        for (Field field : fields) {
            putFieldValue(values, field);
        }

        putGeometry(values);
        IGISApplication app = (IGISApplication) getApplication();

        if (null == app) {
            throw new IllegalArgumentException("Not a IGISApplication");
        }

        Uri uri = Uri.parse(
                "content://" + app.getAuthority() + "/" + mLayer.getPath().getName());

        if (mFeatureId == NOT_FOUND) {
            Uri result = getContentResolver().insert(uri, values);
            if (result == null)
                Toast.makeText(this, getText(R.string.error_db_insert), Toast.LENGTH_SHORT).show();
            else
                mFeatureId = Long.parseLong(result.getLastPathSegment());

            putAttaches();  // we need to get proper mFeatureId for new features first
        } else {
            Uri updateUri = ContentUris.withAppendedId(uri, mFeatureId);
            int attaches = putAttaches();

            if (getContentResolver().update(updateUri, values, null, null) == 0 && attaches == 0) {
                Toast.makeText(this, getText(R.string.error_db_update), Toast.LENGTH_SHORT).show();
            }
        }

        Intent data = new Intent();
        data.putExtra(ConstantsUI.KEY_FEATURE_ID, mFeatureId);
        setResult(RESULT_OK, data);
    }


    protected Object putFieldValue(
            ContentValues values,
            Field field)
    {
        IControl control = mFields.get(field.getName());

        if (null == control) {
            return null;
        }

        Object value = control.getValue();

        if (null != value) {
            Log.d(TAG, "field: " + field.getName() + " value: " + value.toString());

            if (value instanceof Long) {
                values.put(field.getName(), (Long) value);
            } else if (value instanceof Integer) {
                values.put(field.getName(), (Integer) value);
            } else if (value instanceof String) {
                values.put(field.getName(), (String) value);
            }
        }

        return value;
    }


    protected boolean putGeometry(ContentValues values)
    {
        GeoGeometry geometry = null;

        if (null != mGeometry && mIsGeometryChanged) {
            geometry = mGeometry;
        } else if (NOT_FOUND == mFeatureId) {
            if (null == mLocation) {
                Toast.makeText(this, getText(R.string.error_no_location), Toast.LENGTH_SHORT).show();
                return false;
            }

            //create point geometry
            GeoPoint pt;

            switch (mLayer.getGeometryType()) {
                case GeoConstants.GTPoint:
                    pt = new GeoPoint(mLocation.getLongitude(), mLocation.getLatitude());
                    pt.setCRS(GeoConstants.CRS_WGS84);
                    pt.project(GeoConstants.CRS_WEB_MERCATOR);

                    geometry = pt;
                    break;
                case GeoConstants.GTMultiPoint:
                    pt = new GeoPoint(mLocation.getLongitude(), mLocation.getLatitude());
                    pt.setCRS(GeoConstants.CRS_WGS84);
                    pt.project(GeoConstants.CRS_WEB_MERCATOR);

                    GeoMultiPoint mpt = new GeoMultiPoint();
                    mpt.add(pt);
                    geometry = mpt;
                    break;
            }
        }

        if (null != geometry) {
            try {
                values.put(FIELD_GEOM, geometry.toBlob());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    protected int putAttaches() {
        int total = 0;
        PhotoGallery gallery = (PhotoGallery) findViewById(R.id.pg_photos);

        if (gallery != null && mFeatureId != NOT_FOUND) {
            List<Integer> deletedAttaches = gallery.getDeletedAttaches();
            IGISApplication application = (IGISApplication) getApplication();
            Uri uri = Uri.parse("content://" + application.getAuthority() + "/" +
                    mLayer.getPath().getName() + "/" + mFeatureId + "/" + Constants.URI_ATTACH);

            for (Integer attach : deletedAttaches) {
                int result = getContentResolver().delete(Uri.withAppendedPath(uri, attach + ""), null, null);
                total += result;

                if (result == 0) {
                    Log.d(TAG, "attach delete failed");
                } else {
                    Log.d(TAG, "attach delete success: " + result);
                }
            }

            List<String> imagesPath =  gallery.getNewAttaches();

            for (String path : imagesPath) {
                String[] segments = path.split("/");
                String name = segments.length > 0 ? segments[segments.length - 1] : "image.jpg";
                ContentValues values = new ContentValues();
                values.put(VectorLayer.ATTACH_DISPLAY_NAME, name);
                values.put(VectorLayer.ATTACH_MIME_TYPE, "image/jpeg");

                Uri result = getContentResolver().insert(uri, values);
                if (result == null) {
                    Log.d(TAG, "attach insert failed");
                } else {
                    try {
                        OutputStream outStream = getContentResolver().openOutputStream(result);

                        if (outStream != null) {
                            InputStream inStream = new FileInputStream(path);
                            byte[] buffer = new byte[8192];
                            int counter;

                            while ((counter = inStream.read(buffer, 0, buffer.length)) > 0) {
                                outStream.write(buffer, 0, counter);
                                outStream.flush();
                            }

                            outStream.close();
                            inStream.close();
                            total++;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "attach insert success: " + result.toString());
                }
            }
        }

        return total;
    }


    protected void setLocationText(Location location)
    {
        if(null == mLatView || null == mLongView || null == mAccView || null == mAltView)
            return;

        if (null == location) {
            mLatView.setText(formatCoordinates(Double.NaN, R.string.latitude_caption_short));
            mLongView.setText(formatCoordinates(Double.NaN, R.string.longitude_caption_short));
            mAltView.setText(formatMeters(Double.NaN, R.string.altitude_caption_short));
            mAccView.setText(formatMeters(Double.NaN, R.string.accuracy_caption_short));
            return;
        }

        mLocation = location;
        mLatView.setText(formatCoordinates(location.getLatitude(), R.string.latitude_caption_short));
        mLongView.setText(formatCoordinates(location.getLongitude(), R.string.longitude_caption_short));

        mAltView.setText(formatMeters(location.getAltitude(), R.string.altitude_caption_short));
        mAccView.setText(formatMeters(location.getAccuracy(), R.string.accuracy_caption_short));
    }

    private String formatCoordinates(double value, int caption) {
        String appendix;
        if (value != Double.NaN) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int nFormat = prefs.getInt(SettingsConstantsUI.KEY_PREF_COORD_FORMAT + "_int", Location.FORMAT_SECONDS);
            int nFraction = prefs.getInt(SettingsConstantsUI.KEY_PREF_COORD_FRACTION, 6);
            appendix = LocationUtil.formatLatitude(value, nFormat, nFraction, getResources());
        } else
            appendix = getString(R.string.n_a);

        return getString(caption) + ": " + appendix;
    }


    private String formatMeters(double value, int caption) {
        String appendix;
        if (value != Double.NaN) {
            DecimalFormat df = new DecimalFormat("0.0");
            appendix = df.format(value) + " " + getString(R.string.unit_meter);
        } else
            appendix = getString(R.string.n_a);

        return getString(caption) + ": " + appendix;
    }


    @Override
    public void onLocationChanged(Location location)
    {

    }


    @Override
    public void onBestLocationChanged(Location location)
    {

    }


    @Override
    public void onGpsStatusChanged(int event)
    {

    }
}
