package com.alexzaitsev.sample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.alexzaitsev.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.setTopGuy.setOnClickListener {
            binding.meterView1.value = 5678
        }
    }
}