package com.project.creative.hellfireproject;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * The Activity for Vertical Slider display to take input of Hough Transform value
 *
 * @author Wahib-Ul-Haq
 *
 */

public class VerticalSliderActivity extends Activity {

    private Activity mactivity;

    private static final String TAG = "Color_Detection";
    private static final boolean D = true;

    private Button forwardButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);
        if (D)
            Log.e(TAG, "+++ ON CREATE +++");
        mactivity = this;

        setContentView(R.layout.activity_selection);

        forwardButton = (Button)findViewById(R.id.forwardButton);

        forwardButton.setOnClickListener(Click);
    }
    View.OnClickListener Click = new View.OnClickListener(){
        public void onClick(View v){
            Intent intent = new Intent(mactivity, LaneDetector.class);
            intent.putExtra("houghvalue", "150");
            startActivity(intent);
        }

    };


}