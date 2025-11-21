package io.github.idonans.ffa.example.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import io.github.idonans.ffa.example.R
import io.github.idonans.ffa.player.ffab.Ffab
import io.github.idonans.ffa.player.util.LogUtil

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private val mObjectTag by lazy {
        "MainFragment@${System.identityHashCode(this@MainFragment)}"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ffab = Ffab(ffabRawResId = R.raw.animation)
        ffab.prepare(requireContext())
        LogUtil.i {
            "$mObjectTag ffab info: ${ffab.getInfo()}"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val finishView = view.findViewById<View>(R.id.finish)
        finishView.setOnClickListener {
            requireActivity().finish()
        }
    }

}