package consumerphysics.com.myscioapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.consumerphysics.android.sdk.callback.cloud.ScioCloudModelsCallback;
import com.consumerphysics.android.sdk.model.ScioModel;
import com.consumerphysics.android.sdk.sciosdk.ScioCloud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class ModelActivity extends Activity {

    private final static String TAG = ModelActivity.class.getSimpleName();

    public class ModelAdapter extends ArrayAdapter<ScioModel> {
        public ModelAdapter(Context context, List<ScioModel> devices) {
            super(context, 0, devices);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Get the data item for this position
            ScioModel model = getItem(position);

            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.simple_item, parent, false);
            }

            // Lookup view for data population
            TextView tvName = (TextView) convertView.findViewById(R.id.tvName);

            // Populate the data into the template view using the data object
            tvName.setText(model.getCollectionName() + " - " + model.getName());

            // Return the completed view to render on screen
            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model);

        setTitle("Model auswählen");
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();

        List<ScioModel> modelArrayList = new ArrayList<>();
        final ModelAdapter adp = new ModelAdapter(this, modelArrayList);

        final ListView lv = (ListView) findViewById(R.id.listView);
        lv.setAdapter(adp);

        ScioCloud cloud = new ScioCloud(this);

        if (cloud == null || !cloud.hasAccessToken()) {
            Log.d(TAG, "Konnte Model nicht laden. User ist nicht eingeloggt");
            Toast.makeText(getApplicationContext(), "Konnte Model nicht laden. User ist nicht eingeloggt", Toast.LENGTH_SHORT).show();
            return;
        }

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ScioModel model = adp.getItem(position);
                storeSelectedModel(model);
                Toast.makeText(getApplicationContext(), model.getName() + " wurde ausgewählt", Toast.LENGTH_SHORT).show();
                //finish();
                JumpToAnalyse(model.getName());


            }
        });


        cloud.getModels(new ScioCloudModelsCallback() {
            @Override
            public void onSuccess(List<ScioModel> models) {

                List<ScioModel> cheese = selectModell(models, "Hartkäse - EST");

                adp.addAll(cheese);
            }

            @Override
            public void onError(int code, String msg) {
                Log.e(TAG, "Error " + code + " while retrieving models :" + msg);
                Toast.makeText(getApplicationContext(), "Fehler beim auslesen des Modells", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void storeSelectedModel(final ScioModel model) {
        SharedPreferences pref = getSharedPreferences("MyPref", Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();

        edit.putString("model_id", model.getId());
        edit.putString("model_name", model.getCollectionName() + " - " + model.getName());


        edit.commit();
    }

    //New Methodes

    private List<ScioModel> selectModell(List<ScioModel> models, String choose)
    {

       for(Iterator<ScioModel> it=models.iterator(); it.hasNext();){
            if( !it.next().getCollectionName().contains(choose)) {

                it.remove();
            }
        }

        return models;
    }

    private void JumpToAnalyse(String model){

        if(model.contains("CLA")){

            Intent clasi = new Intent(ModelActivity.this, ClassificationActivity.class);
            startActivity(clasi);



        }
        else if(model.contains("EST")){

          //  Intent esti = new Intent(ModelActivity.this, EstimationActivity.class);
          //  startActivity(esti);
        }

        else {

            Intent milk = new Intent(ModelActivity.this, MainActivity.class);
            startActivity(milk);
            finish();

        }

    }



}
