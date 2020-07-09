package org.schabi.testapp

import android.Manifest
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import org.coralibre.android.sdk.PPCP

import org.coralibre.android.sdk.internal.TracingService;
import org.coralibre.android.sdk.internal.TracingService.ACTION_START

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 0);

        findViewById<Button>(R.id.start_service_button).setOnClickListener {
            PPCP.init(this);
            val serviceIntent = Intent(this, TracingService::class.java).apply {
                action = ACTION_START
            }
            startService(serviceIntent);
        }
    }
}
