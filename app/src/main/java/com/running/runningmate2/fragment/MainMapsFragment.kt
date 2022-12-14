package com.running.runningmate2.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.ktx.addPolyline
import com.running.domain.model.DomainWeather
import com.running.runningmate2.MainActivity
import com.running.runningmate2.MyApplication
import com.running.runningmate2.R
import com.running.runningmate2.RunningData
import com.running.runningmate2.databinding.FragmentMapsBinding
import com.running.runningmate2.fragment.viewModel.MainStartViewModel
import com.running.runningmate2.utils.EventObserver
import com.running.runningmate2.viewModel.MainViewModel
import java.lang.Math.round
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class MainMapsFragment : Fragment(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private var mainMarker: Marker? = null
    private var nowPointMarker: Marker? = null
    private val mainStartViewModel: MainStartViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val binding: FragmentMapsBinding by lazy {
        FragmentMapsBinding.inflate(layoutInflater)
    }
    private var start: Boolean = false
    private var myNowLati: Double? = null
    private var myNowLong: Double? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private var weatherData: DomainWeather? = null
    private var static = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainStartViewModel.end = 0
        Log.e(javaClass.simpleName, "onCreateView")

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true && it[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
                mainStartViewModel.repeatCallLocation()
            else {
                Toast.makeText(requireContext(), "?????? ????????? ????????? ????????? ?????? ??? ??? ????????????. :)", Toast.LENGTH_SHORT)
                    .show()
                requireActivity().finish()
            }
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        binding.loadingText.visibility = View.VISIBLE
        binding.setBtn.visibility = View.INVISIBLE

        // start ??????
        binding.startButton.setOnClickListener {
//            mainStartViewModel.test()
            if (binding.startButton.text == "Start") {
                Log.e(javaClass.simpleName, "Start ?????? ?????????")
                val bottomSheet = com.running.runningmate2.BottomSheet(mainViewModel.getWeight()) {
                    mainViewModel.setData(it)
                }
                bottomSheet.show(parentFragmentManager, bottomSheet.tag)
                //stop??????
            } else {
                Log.e(javaClass.simpleName, "stop ?????? ?????????")
                viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                    val nowTime =
                        "${LocalDate.now()} ${LocalTime.now().hour}:${LocalTime.now().minute}"
                    val dayOfWeek =
                        when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toString()) {
                            "1" -> "???"
                            "2" -> "???"
                            "3" -> "???"
                            "4" -> "???"
                            "5" -> "???"
                            "6" -> "???"
                            else -> "???"
                        }
                    val datas = RunningData(
                        dayOfWeek,
                        nowTime,
                        binding.runingBox.runTimeText.text.toString(),
                        binding.runingBox.runDistanceText.text.toString(),
                        binding.runingBox.runCaloreText.text.toString(),
                        binding.runingBox.runStepText.text.toString()
                    )
                    Log.e(javaClass.simpleName, "stop btn ?????? : $datas")
                    mainViewModel.insertDB(datas)
                    mainStartViewModel.end = 1
                    (activity as MainActivity).changeFragment(2)
                    start = false
                    mainStartViewModel.stepInit()
                }
            }
        }
        return binding.root
    }


    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.e(javaClass.simpleName, "onViewCreated")
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.error.observe(viewLifecycleOwner, EventObserver {
            //?????? ?????????
            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        })

        mainViewModel.success.observe(viewLifecycleOwner, EventObserver {
            runningStart()
            binding.startButton.text = "Stop"
            binding.fake.text = "\n"
        })

        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        mainViewModel.getWeatherData.observe(viewLifecycleOwner) { weather ->
            weatherData = weather
        }

        mainViewModel.getWeatherData.observe(viewLifecycleOwner) { myData ->
            binding.weatherView.loadingIcon.visibility = View.INVISIBLE
            binding.startButton.visibility = View.VISIBLE
            binding.weatherView.weatherIcon.visibility = View.VISIBLE
            if (myData?.temperatures == null) {
                binding.weatherView.weatherTem.text = "loading.."
            } else
                binding.weatherView.weatherTem.text =
                    "${myData.temperatures.toDouble()?.let { round(it) }} ??c"

            if (myData?.humidity == null) {
                binding.weatherView.humidity.text = "loading.."
            } else
                binding.weatherView.humidity.text = "${myData.humidity} %"

            when (myData?.rainType?.toDouble()?.toInt()) {
                //0 ??????, 1 ?????????, 2367 ???, 5 ???
                0 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic___weather_suncloude
                )
                1 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic___weather__strongrain
                )
                2 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic__weather_snow
                )
                3 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic__weather_snow
                )
                5 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic___weather_rain
                )
                6 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic__weather_snow
                )
                7 -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic__weather_snow
                )
                else -> binding.weatherView.weatherIcon.background = ContextCompat.getDrawable(
                    MyApplication.getApplication(),
                    R.drawable.ic___weather_suncloude
                )
            }
            Log.e(javaClass.simpleName, "????????? ?????? ????????? : $myData")
        }
    }


    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMapReady(googleMap: GoogleMap) {
        static = false
        binding.fake.text = "\n\n\n\n"

        //?????? ????????? ??? ????????? ??????
        binding.setBtn.setOnClickListener {
            //?????? ?????? ????????? ???
            if (start) {
                Log.e("TAG", "onCreateView: start ????????????")
                Toast.makeText(requireContext(), "??? ?????????", Toast.LENGTH_SHORT).show()
                mainStartViewModel.setNowBtn.observe(viewLifecycleOwner) { locations ->
                    val myLocation = LatLng(locations.latitude - 0.0006, locations.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 17.5F))
                    mainStartViewModel.setNowBtn.removeObservers(viewLifecycleOwner)
                }
                //?????? ??? ?????? ???
            } else {
                Log.e("TAG", "onCreateView: stop ????????????")
                Toast.makeText(requireContext(), "??? ?????????", Toast.LENGTH_SHORT).show()
                mainStartViewModel.setNowBtn.observe(viewLifecycleOwner) { locations ->
                    val myLocation = LatLng(locations.latitude, locations.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 17F))
                    mainStartViewModel.setNowBtn.removeObservers(viewLifecycleOwner)
                }
            }
        }

        binding.startButton.visibility = View.INVISIBLE
        binding.followBtn.visibility = View.INVISIBLE
        mMap = googleMap

        if (view != null) {
            mainStartViewModel.location.observe(viewLifecycleOwner) { locations ->
                if (locations.size > 0 && weatherData == null) {
                    Log.e(javaClass.simpleName, "?????? ??????")
                    mainViewModel.getWeatherData(locations.first())
                    binding.weatherView.weatherTem
                }

                if (locations.isNotEmpty()) {
                    binding.loadingText.visibility = View.INVISIBLE

                    if (!start) {
                        binding.startButton.visibility = View.VISIBLE
                    }

                    binding.setBtn.visibility = View.VISIBLE

                    myNowLati = locations.last().latitude
                    myNowLong = locations.last().longitude

                    LatLng(locations.last().latitude, locations.last().longitude).also {
                        // start????????? setLatLng????????? ??? ?????? ?????? ????????? ???
                        if (start) {
                            Log.e(javaClass.simpleName, "observe setLatLng start")
                            mainStartViewModel.setLatLng(it)
                        } else {
                            // ????????? ?????? ????????? ??????
                            if (locations.size == 1) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 17F))
                            }
                            mMap.clear()
                            mainMarker = mMap.addMarker(
                                MarkerOptions()
                                    .position(LatLng(it.latitude, it.longitude))
                                    .title("pureum")
                                    .alpha(0.9F)
                                    .icon(
                                        bitmapDescriptorFromVector(
                                            requireContext(),
                                            R.drawable.ic_twotone_mylocate
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }

        if(view!=null){
        mainStartViewModel.latLng.observe(viewLifecycleOwner) { latlngs ->
            if (latlngs.isNotEmpty()) {
//                nowPointMarker?.remove()
                nowPointMarker?.remove()
                nowPointMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(latlngs.last().latitude, latlngs.last().longitude))
                        .title("pureum")
                        .alpha(0.9F)
                    //????????? ????????? ?????? ?????????
                        .icon(bitmapDescriptorFromVector(requireContext(),R.drawable.ic_twotone_mylocate))
                )
                if (latlngs.size > 1) {
                    val beforeLocate = Location(LocationManager.NETWORK_PROVIDER)
                    val afterLocate = Location(LocationManager.NETWORK_PROVIDER)
                    beforeLocate.latitude = latlngs[latlngs.lastIndex-1].latitude
                    beforeLocate.longitude = latlngs[latlngs.lastIndex-1].longitude
                    afterLocate.latitude = latlngs[latlngs.lastIndex].latitude
                    afterLocate.longitude = latlngs[latlngs.lastIndex].longitude
                    val result = beforeLocate.distanceTo(afterLocate).toDouble()
                    Log.e("TAG", "?????? : $result", )
                    if(result >= 0) {
                        mMap.addPolyline {
                            add(latlngs[latlngs.lastIndex - 1], latlngs[latlngs.lastIndex])
                            width(20F)
                            startCap(RoundCap())
                            endCap(RoundCap())
                            color(Color.parseColor("#FA785F"))
                        }
                    }
                }
            }
        }
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }


    @SuppressLint("SetTextI18n")
    fun runningStart() {
        start = true
        mMap.clear()
        (activity as MainActivity).changeFragment(1)
        mainStartViewModel.myTime()
        mainStartViewModel.myStep()

        //???????????? ??????
        binding.followBtn.visibility = View.VISIBLE
        binding.followBtn.setOnClickListener {
            if (!static) {
                Toast.makeText(requireContext(), "?????? ?????? ?????? ON", Toast.LENGTH_SHORT).show()
                mainStartViewModel.fixDisplayBtn.observe(viewLifecycleOwner) { locations ->
                    val myLocation =
                        LatLng(locations.latitude - 0.0003, locations.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 18F))
                }
                binding.followBtn.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.shape_click_btn)

                static = true
            } else {
                Toast.makeText(requireContext(), "?????? ?????? ?????? OFF", Toast.LENGTH_SHORT).show()
                mainStartViewModel.fixDisplayBtn.observe(viewLifecycleOwner) { locations ->
                    val myLocation =
                        LatLng(locations.latitude - 0.0006, locations.longitude)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 17.5F))
                }
                mainStartViewModel.fixDisplayBtn.removeObservers(viewLifecycleOwner)
                binding.followBtn.background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.shape_set_btn)
                static = false
            }
        }
        val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.blink)

        //????????? ????????? ?????? ??????
        if (myNowLong != null && myNowLong != null) {
            val startZoom = LatLng(myNowLati!! - 0.0006, myNowLong!!)
            val startLocate = LatLng(myNowLati!!, myNowLong!!)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startZoom, 17.5F))
            nowPointMarker = mMap.addMarker(
                MarkerOptions()
                    .position(startLocate)
                    .title("?????? ??????")
                    .alpha(0.9F)
                //????????? ????????? ?????? ?????????
//                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.newicon))
                    .icon(bitmapDescriptorFromVector(requireContext(),R.drawable.ic_twotone_mylocate))
            )
        }
        binding.textConstraint.visibility = View.VISIBLE

        mainStartViewModel.time.observe(viewLifecycleOwner) { time ->
            if (time != null) {
                binding.runingBox.runTimeText.text = time
            }
        }

        mainStartViewModel.calorie.observe(viewLifecycleOwner) { calorie ->
            Log.e("TAG", "?????????????????? $calorie")
            if (calorie.toString().length > 4)
                binding.runingBox.runCaloreText.text = "${String.format("%.2f", calorie)} Kcal"
            else
                binding.runingBox.runCaloreText.text = "$calorie Kcal"

        }

        // ?????? ??????
        mainStartViewModel.distance.observe(viewLifecycleOwner) { distance ->
            if (distance != null) {
                binding.runingBox.runDistanceText.text = "${String.format("%.2f", distance)} M"
            }
        }

        mainStartViewModel.step.observe(viewLifecycleOwner) {
            if (it != null) {
                binding.runingBox.runStepText.text = "$it ??????"
            }
        }
    }
}