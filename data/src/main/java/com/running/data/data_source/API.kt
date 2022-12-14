package com.running.data.data_source
import com.running.data.dto.Weather
import retrofit2.http.GET
import retrofit2.http.QueryMap

interface API {

    /**
     * ServiceKey
     * pageNo
     * numOfRows
     * dataType
     * base_date
     * base_time
     * nx
     * ny
     */

    //쿼리 물음표
    //path 슬래시
    //쿼리는 하나
    //쿼리는 여러개
    @GET("1360000/VilageFcstInfoService_2.0/getUltraSrtNcst")
    suspend fun getWeatherData(@QueryMap data: HashMap<String, String>): Weather
// 코루틴 위에서 동작하므로 suspend를 안붙인다고 빌드 오류는 안나지만
//데이터가 정상적으로 안들어와 문제 발생함.
}