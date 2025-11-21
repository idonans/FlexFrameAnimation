package io.github.idonans.ffa.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.idonans.ffa.example.ui.main.MainFragment
import io.github.idonans.ffa.player.Debug

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Debug.enable = true

        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance()).commitNow()
        }
    }

}