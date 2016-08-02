package consumerphysics.com.myscioapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.consumerphysics.android.sdk.callback.cloud.ScioCloudAnalyzeCallback;
import com.consumerphysics.android.sdk.callback.cloud.ScioCloudUserCallback;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceCalibrateHandler;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceCallback;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceConnectHandler;
import com.consumerphysics.android.sdk.callback.device.ScioDeviceScanHandler;
import com.consumerphysics.android.sdk.model.ScioModel;
import com.consumerphysics.android.sdk.model.ScioReading;
import com.consumerphysics.android.sdk.model.ScioUser;
import com.consumerphysics.android.sdk.model.attribute.ScioDatetimeAttribute;
import com.consumerphysics.android.sdk.model.attribute.ScioNumericAttribute;
import com.consumerphysics.android.sdk.model.attribute.ScioStringAttribute;
import com.consumerphysics.android.sdk.sciosdk.ScioCloud;
import com.consumerphysics.android.sdk.sciosdk.ScioDevice;
import com.consumerphysics.android.sdk.sciosdk.ScioLoginActivity;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final static int LOGIN_ACTIVITY_RESULT = 1000;

    // TODO: Put your redirect url here!
    private static final String REDIRECT_URL = "http://www.consumerphysics.com";

    // TODO: Put your app key here!
    private static final String APPLICATION_KEY = "82ad7ad3-613d-41b1-8df0-515116d1dcd2";

    // Scio
    private ScioDevice scioDevice;
    private ScioCloud scioCloud;

    // UI
    private TextView tvName;
    private TextView tvAddress;
    private TextView tvStatus;
    private TextView tvUsername;
    private TextView tvModel;

    private CheckBox cbUser;
    private CheckBox cbScio;

    private ProgressDialog loader;

    // Members
    private String scioName;
    private String scioAddress;
    private String username;
    private String modelId;
    private String modelName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initUI();
        initScioCloud();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (scioCloud.hasAccessToken() && username == null) {
            getScioUser();
        }

        updateDisplay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case LOGIN_ACTIVITY_RESULT:
                if (resultCode == RESULT_OK) {
                    Log.d(TAG, "We are logged in.");
                } else {
                    // Should usually occur only if not internet is available.
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final String description = data.getStringExtra(ScioLoginActivity.ERROR_DESCRIPTION);
                            final int errorCode = data.getIntExtra(ScioLoginActivity.ERROR_CODE, -1);
                            Toast.makeText(MainActivity.this, "An error has occurred.\nError code: " + errorCode + "\nDescription: " + description, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Make sure scio device is disconnected or leaks may occur
        if (scioDevice != null) {
            scioDevice.disconnect();

            Log.d("Milch", "disconnect in Main");

            scioDevice = null;
        }

        if (loader != null) {
            loader.cancel();
            loader = null;
        }
    }

    public void doLogout(final View view) {
        Log.d(TAG, "doLogout");

        if (scioCloud != null) {
            scioCloud.deleteAccessToken();

            storeUsername(null);
            updateDisplay();
        }
    }

    public void doLogin(final View view) {
        Log.d(TAG, "doLogin");

        if (!scioCloud.hasAccessToken()) {
            final Intent intent = new Intent(this, ScioLoginActivity.class);
            intent.putExtra(ScioLoginActivity.INTENT_REDIRECT_URI, REDIRECT_URL);
            intent.putExtra(ScioLoginActivity.INTENT_APPLICATION_ID, APPLICATION_KEY);

            startActivityForResult(intent, LOGIN_ACTIVITY_RESULT);
        } else {
            Log.d(TAG, "Already have token");

            getScioUser();
        }
    }

    public void doDiscover(View view) {
        Intent intent = new Intent(this, DiscoverActivity.class);
        startActivity(intent);
    }

    public void doModels(final View view) {


      /*  if (scioCloud == null || !scioCloud.hasAccessToken()) {
            Log.d(TAG, "User muss sich an SCIO Cloud einloggen");
            Toast.makeText(getApplicationContext(), "User muss sich an SCIO Cloud einloggen", Toast.LENGTH_SHORT).show();
       */

        if (!cbUser.isChecked() && !cbScio.isChecked()) {
            Toast.makeText(getApplicationContext(), "User muss sich im SCIO-Scanner verbinden und sich mit SCIO-Cloud verbinden", Toast.LENGTH_SHORT).show();
        } else if (!cbScio.isChecked()) {
            Toast.makeText(getApplicationContext(), "User muss sich im SCIO-Scanner verbinden", Toast.LENGTH_SHORT).show();
        } else if (!cbUser.isChecked()) {
            Toast.makeText(getApplicationContext(), "User muss sich im SCIO-Cloud verbinden", Toast.LENGTH_SHORT).show();
        } else {

            final Intent intent = new Intent(this, ModelActivity.class);
            startActivity(intent);
        }
    }

    public void doConnect(final View view) {
        if (scioAddress == null) {
            Toast.makeText(getApplicationContext(), "Es wurde kein SCIO ausgewählt", Toast.LENGTH_SHORT).show();
            return;
        }

        scioDevice = new ScioDevice(this, scioAddress);

        scioDevice.setButtonPressedCallback(new ScioDeviceCallback() {
            @Override
            public void execute() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplay();
                        Toast.makeText(getApplicationContext(), "SCiO-Button wurde gedrückt", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        loader = ProgressDialog.show(this, "Bitte Warten!", "Verbinden...", false);

        scioDevice.connect(new ScioDeviceConnectHandler() {
            @Override
            public void onConnected() {
                Log.d(TAG, "Erfolgreich mit SCIO verbunden");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplay();
                        Toast.makeText(getApplicationContext(), "Erfolgreich mit SCIO verbunden", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }

            @Override
            public void onConnectFailed() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplay();
                        Toast.makeText(getApplicationContext(), "Konnte nicht mit SCIO verbunden werden", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }

            @Override
            public void onTimeout() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateDisplay();
                        Toast.makeText(getApplicationContext(), "Verbindung im Time-Out", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }
        });
    }

    public void doDisconnect(final View view) {
        if (scioDevice == null || !scioDevice.isConnected()) {
            Toast.makeText(getApplicationContext(), "Keine Verbindung mit SCIO", Toast.LENGTH_SHORT).show();
            return;
        }

        loader = ProgressDialog.show(this, "Bitte Warten!", "Verbinden...", false);

        scioDevice.setScioDisconnectCallback(new ScioDeviceCallback() {
            @Override
            public void execute() {
                Toast.makeText(getApplicationContext(), "Mit SCIO getrennt", Toast.LENGTH_SHORT).show();
                loader.cancel();
                updateDisplay();
            }
        });

        scioDevice.disconnect();
    }

    public void doCalibrate(final View view) {
        if (scioDevice == null || !scioDevice.isConnected()) {
            Log.d(TAG, "Konnte nicht Kalibriert werden. SCiO ist nicht verbunden");
            Toast.makeText(getApplicationContext(), "Konnte nicht Kalibriert werden. SCiO ist nicht verbunden", Toast.LENGTH_SHORT).show();
            return;
        }


        loader = ProgressDialog.show(this, "Bitte Warten!", "Kalibrieren...", false);

        scioDevice.calibrate(new ScioDeviceCalibrateHandler() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "SCiO wurde erfolgreich kalibriert");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "SCiO wurde erfolgreich kalibriert", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }

            @Override
            public void onError() {
                Log.e(TAG, "Fehler beim Kalibrieren");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Fehler beim Kalibrieren", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }

            @Override
            public void onTimeout() {
                Log.e(TAG, "Timeout während des Kalibieren");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Timeout während des Kalibieren", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }
        });
    }

    public class ScioModelAdapter extends ArrayAdapter<ScioModel> {
        public ScioModelAdapter(final Context context, final List<ScioModel> devices) {
            super(context, 0, devices);
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.results_item, parent, false);
            }

            final TextView attributeName = (TextView) convertView.findViewById(R.id.attribute_name);
            final TextView attributeValue = (TextView) convertView.findViewById(R.id.attribute_value);

            final ScioModel model = getItem(position);

            final String value;
            String unit = null;

            /**
             * Classification model will return a STRING value.
             * Estimation will return the NUMERIC value.
             */
            switch (model.getAttributes().get(position).getAttributeType()) {
                case STRING:
                    value = ((ScioStringAttribute) (model.getAttributes().get(position))).getValue();
                    break;
                case NUMERIC:
                    value = String.valueOf(((ScioNumericAttribute) (model.getAttributes().get(position))).getValue());
                    unit = model.getAttributes().get(position).getUnits();
                    break;
                case DATE_TIME:
                    value = ((ScioDatetimeAttribute) (model.getAttributes().get(position))).getValue().toString();
                    break;
                default:
                    value = "Unknown";
            }

            attributeName.setText(model.getName());

            if (model.getType().equals(ScioModel.Type.ESTIMATION)) {
                attributeValue.setText(value + unit);
            } else {
                attributeValue.setText(value);
            }

            return convertView;
        }
    }


    public void doScan(final View view) {

        if (scioDevice == null || !scioDevice.isConnected()) {
            Log.d(TAG, "Konnte nicht Scannen. SCiO ist nicht verubnden");
            Toast.makeText(getApplicationContext(), "Konnte nicht Scannen. SCiO ist nicht verubnden", Toast.LENGTH_SHORT).show();
            return;
        }

        if (modelId == null) {
            Log.d(TAG, "Konnte nicht Scannen. Es wurde kein Model ausgewählt");
            Toast.makeText(getApplicationContext(), "Konnte nicht Scannen. Es wurde kein Model ausgewählt", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scioCloud == null || !scioCloud.hasAccessToken()) {
            Log.d(TAG, "Konnte nicht Scannen. User ist nicht mit Cloud verbunden");
            Toast.makeText(getApplicationContext(), "Konnte nicht Scannen. User ist nicht mit Cloud verbunden", Toast.LENGTH_SHORT).show();
            return;
        }

        loader = ProgressDialog.show(this, "Bitte Warten!", "Scannen...",false);

        scioDevice.scan(new ScioDeviceScanHandler() {
            @Override
            public void onSuccess(final ScioReading reading) {
                Log.d(TAG, "onSuccess");

                scioCloud.analyze(reading, modelId, new ScioCloudAnalyzeCallback() {
                    @Override
                    public void onSuccess(final ScioModel model) {
                        Log.d(TAG, "analyze onSuccess");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showAnalyzeResults(model);
                                loader.cancel();
                            }
                        });
                    }

                    @Override
                    public void onError(final int code, final String msg) {
                        Log.d(TAG, "analyze onError");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Fehler beim Analysieren " + msg, Toast.LENGTH_SHORT).show();
                                loader.cancel();
                            }
                        });

                    }
                });
            }

            @Override
            public void onNeedCalibrate() {
                Log.d(TAG, "onNeedCalibrate");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Konnte nicht scannen. Bitte Kalibrieren!", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }

            @Override
            public void onError() {
                Log.d(TAG, "onError");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Fehler beim Scannen", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });

            }

            @Override
            public void onTimeout() {
                Log.d(TAG, "onTimeout");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Timeout beim Scannen", Toast.LENGTH_SHORT).show();
                        loader.cancel();
                    }
                });
            }
        });
    }

    private void getScioUser() {
        loader = ProgressDialog.show(this, "Bitte Warten", "Lade User...", false);

        scioCloud.getScioUser(new ScioCloudUserCallback() {
            @Override
            public void onSuccess(final ScioUser user) {
                Log.d(TAG, "First name=" + user.getFirstName() + "  Last name=" + user.getLastName());
                Toast.makeText(getApplicationContext(), "Willkommen " + user.getFirstName() + " " + user.getLastName(), Toast.LENGTH_SHORT).show();

                storeUsername(user.getUsername());
                updateDisplay();

                loader.cancel();
            }

            @Override
            public void onError(final int code, final String message) {
                Toast.makeText(getApplicationContext(), "Fehler beim Laden von den Userinformationen", Toast.LENGTH_SHORT).show();
                loader.cancel();
            }
        });
    }

    private void showAnalyzeResults(final ScioModel model) {
        final AlertDialog.Builder dlg = new AlertDialog.Builder(this);

        dlg.setTitle("Results");

        final LayoutInflater inflater = getLayoutInflater();
        final View convertView = inflater.inflate(R.layout.results_view, null);
        dlg.setView(convertView);

        final ArrayList<ScioModel> arrayOfModels = new ArrayList<>();
        final ScioModelAdapter adp = new ScioModelAdapter(this, arrayOfModels);

        final ListView lv = (ListView) convertView.findViewById(R.id.lvResults);
        lv.setAdapter(adp);

        adp.addAll(model);

        dlg.setPositiveButton("OK", null);
        dlg.setCancelable(true);
        dlg.create().show();
    }

    private void initScioCloud() {
        scioCloud = new ScioCloud(this);
    }

    private void initUI() {
        setContentView(R.layout.activity_main);

        tvName = (TextView) findViewById(R.id.tv_scio_name);
        tvAddress = (TextView) findViewById(R.id.tv_scio_address);
        tvStatus = (TextView) findViewById(R.id.tv_scio_status);
        tvUsername = (TextView) findViewById(R.id.tv_username);
        tvModel = (TextView) findViewById(R.id.tv_model);

        cbScio = (CheckBox) findViewById(R.id.cb_scio);
        cbUser = (CheckBox) findViewById(R.id.cb_user);


    }

    private void storeUsername(final String username) {
        this.username = username;

        final SharedPreferences pref = this.getSharedPreferences("MyPref", Context.MODE_PRIVATE);
        final SharedPreferences.Editor edit = pref.edit();

        edit.putString("username", username);

        edit.commit();

    }

    private void updateDisplay() {
        Log.d(TAG, "Updating the display");

        final SharedPreferences pref = this.getSharedPreferences("MyPref", Context.MODE_PRIVATE);

        scioName = pref.getString("scio_name", null);
        scioAddress = pref.getString("scio_address", null);

        username = pref.getString("username", null);

        if (username == null) {
            cbUser.setChecked(false);
        } else {
            cbUser.setChecked(true);
        }

        modelName = pref.getString("model_name", null);
        modelId = pref.getString("model_id", null);


        tvName.setText(scioName);
        tvAddress.setText(scioAddress);
        tvUsername.setText(username);
        tvModel.setText(modelName);

        if (scioDevice == null || !scioDevice.isConnected()) {

      //      tvStatus.setText("Trennen");
            tvName.setText("Mit SCIO verbinden...");
            cbScio.setChecked(false);
        } else {
        //    tvStatus.setText("Verbinden");
            cbScio.setChecked(true);
        }
    }
}