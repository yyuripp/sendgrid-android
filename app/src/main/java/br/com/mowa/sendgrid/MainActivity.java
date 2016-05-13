package br.com.mowa.sendgrid;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final TextView hl = (TextView) findViewById(R.id.hello);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                SendGrid sendGrid = new SendGrid("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");
                SendGrid.Email email = new SendGrid.Email();
                email.getSMTPAPI();

                try {
                    email.addSmtpApiTo("ygor.pessoa@mowa.com.br");
                    email.setTemplateId("6325ef2a-f5a7-4681-baa2-75c3e4f66bda");
                    email.setFrom("teste@teste.com");
                    email.setText("TOKEN");
                    email.setSubject(" ");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                sendGrid.send(email, new ResponseSendGrid.Listener() {
                    @Override
                    public void onResponse(JSONObject response) {

                        Log.d("sendGrid response" , response.toString());
                    }
                }, new ResponseSendGrid.ErrorListener() {
                    @Override
                    public void onErrorResponse(SendGrid.SendGridException error) {
                        Log.d("sendGrid errorresponse" , error.toString());
                    }
                });

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
