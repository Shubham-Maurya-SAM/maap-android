package com.shubham.maap

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.shubham.maap.databinding.FragmentSplashBinding

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startSplashAnimation()
    }

    private fun startSplashAnimation() {
        // 1. Show English Name (Fade In)
        val animEnIn = ObjectAnimator.ofFloat(binding.tvSplashNameEn, View.ALPHA, 0f, 1f).apply {
            duration = 700
        }

        // 2. Hide English Name (Vanish)
        val animEnOut = ObjectAnimator.ofFloat(binding.tvSplashNameEn, View.ALPHA, 1f, 0f).apply {
            duration = 500
            startDelay = 1000 // Show for 1 sec
        }

        // 3. Show Hindi Name (Fade In)
        val animHiIn = ObjectAnimator.ofFloat(binding.tvSplashNameHi, View.ALPHA, 0f, 1f).apply {
            duration = 700
        }

        // 4. Measuring Tape Animation (Expanding Line)
        val tapeAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                binding.vTapeLine.alpha = 1f
                binding.vTapeLine.scaleX = progress
            }
        }

        // 5. Logo Animation (Pulse)
        val logoPulse = ObjectAnimator.ofFloat(binding.ivSplashLogo, View.SCALE_X, 1f, 1.1f, 1f).apply {
            duration = 1000
            repeatCount = 1
        }

        // Sequence construction
        val enSet = AnimatorSet().apply {
            playTogether(animEnIn, logoPulse)
        }

        val hiSet = AnimatorSet().apply {
            playTogether(animHiIn, tapeAnim)
        }

        AnimatorSet().apply {
            playSequentially(enSet, animEnOut, hiSet)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAdded) {
                        binding.root.postDelayed(
                            {
                                if (isAdded) {
                                    findNavController().navigate(R.id.action_splashFragment_to_homeFragment)
                                }
                            },
                            800
                        )
                    }
                }
            })
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
