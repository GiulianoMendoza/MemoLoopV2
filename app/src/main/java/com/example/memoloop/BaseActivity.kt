package com.example.memoloop

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_base)

        setupBottomToolbar()
    }

    override fun setContentView(view: View?) {
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        contentFrame.removeAllViews()
        contentFrame.addView(view)
    }

    override fun setContentView(layoutResID: Int) {
        val view = LayoutInflater.from(this).inflate(layoutResID, null)
        setContentView(view)
    }





    private fun setupBottomToolbar() {
        findViewById<ImageButton>(R.id.btn_home)?.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_history)?.setOnClickListener {
            startActivity(Intent(this, RemindersActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_config)?.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
