package com.whispercppdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.whispercppdemo.ui.main.MainScreen
import com.whispercppdemo.ui.main.MainScreenViewModel
import com.whispercppdemo.ui.theme.WhisperCppDemoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainScreenViewModel by viewModels { MainScreenViewModel.factory() }
    private val testViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Run the Whisper model loading test
        testViewModel.testLoadModel()

        setContent {
            WhisperCppDemoTheme {
                MainScreen(viewModel)
            }
        }
    }
}