package com.tencent.mna.lib.devices

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.*
import android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION
import android.text.TextUtils
import com.bihe0832.android.lib.log.ZLog
import com.bihe0832.android.lib.network.IpUtils
import com.bihe0832.android.lib.network.NetworkUtil
import com.bihe0832.android.lib.network.WifiUtil
import com.bihe0832.android.lib.thread.ThreadManager
import java.util.*

/**
 * @author hardyshi code@bihe0832.com
 * Created on 2019-05-31.
 * Description: Description
 */

class WifiManagerWrapper private constructor() {

    val TAG = "WifiManager-> "
    val DEFAULT_SSID = "<unknown ssid>"
    val NETWORK_CLASS_NULL = 0
    val NETWORK_CLASS_UNKNOWN = 0
    val NETWORK_CLASS_2G = 1
    val NETWORK_CLASS_3G = 2
    val NETWORK_CLASS_4G = 3
    val NETWORK_CLASS_WIFI = 4
    val NETWORK_CLASS_WIFI_4G = 5//用于上报
    val NETWORK_CLASS_WIFI_WIFI = 6//用于上报
    val NETWORK_CLASS_5G = 7
    val NETWORK_CLASS_BLUETOOTH = 8
    val NETWORK_CLASS_ETHERNET = 9

    val NETWORK_NAME_UNKNOWN = "Unknown"
    val NETWORK_NAME_2G = "2G"
    val NETWORK_NAME_3G = "3G"
    val NETWORK_NAME_4G = "4G"
    val NETWORK_NAME_WIFI = "WiFi"

    private var mContext: Context? = null

    private var mWifiManager: WifiManager? = null
    private var mConnectivityManager: ConnectivityManager? = null
    private var mWifiReceiver: NetworkBroadcastReceiver? = null
    private var mWifiChangeListener: OnWifiChangerListener? = null

    private var isDebug = false
    private var mNotifyRSSI = false
    // 扫描出的网络连接列表
    private var mWifiList: List<ScanResult>? = null
    // 已经连接的网络连接列表
    private var mWifiConfigurationList: List<WifiConfiguration>? = null
    // 当前wifi的状态
    //获取wifi状态，开启关闭等
    var wifiState = WifiManager.WIFI_STATE_DISABLED
        internal set
    // 当前连接热点的信息
    private var mWifiInfo: WifiInfo? = null
    private var mWiFiNetworkInfo: NetworkInfo? = null

    interface OnWifiChangerListener {
        /**
         * 返回当前手机的Wi-Fi状态
         * @param state 当前Wi-F状态 ，取值参考 [WifiManager.getWifiState].
         */
        fun onStateChanged(state: Int)

        /**
         * 当前连接的Wi-Fi热点的信息发生变化
         * 收到回调以后，可调用 [.getSSID] 等获取最新信息
         */
        fun onWifiInfoChanged()

        /**
         * Wi-Fi扫描时发现了新的Wi-Fi
         * @param wifiList 返回最新的可发现的Wi-Fi列表
         */
        fun onScanUpdate(wifiList: List<ScanResult?>?)

        /**
         * 返回手机已保存的Wi-Fi网络列表
         * @param wifiConfigurationList 返回最新的已保存的Wi-Fi网络列表
         */
        fun onConnectUpdate(wifiConfigurationList: List<WifiConfiguration?>?)
    }

    fun setWifiChangedListener(listener: OnWifiChangerListener?) {
        if(null == listener){
            mWifiChangeListener = null
        }else{
            mWifiChangeListener = listener
            updateBaseInfo()
            updateScanListInfo()
            updateConfiguredListInfo()
            listener.onWifiInfoChanged()
            listener.onScanUpdate(getScanResultList())
            listener.onConnectUpdate(getConfigurationList())
        }
    }

    // 得到可以直连的网络
    fun getConfigurationList(): List<WifiConfiguration?> {
        return when (mWifiConfigurationList) {
            null -> ArrayList()
            is List<WifiConfiguration> -> mWifiConfigurationList as List<WifiConfiguration?>
            else -> ArrayList()
        }
    }

    // 得到发现网络列表
    fun getScanResultList(): List<ScanResult> {
        return when (mWifiList) {
            null -> ArrayList()
            is List<ScanResult> -> mWifiList as List<ScanResult>
            else -> ArrayList()
        }
    }

    //Wifi的名称
    fun getSSID(): String {
        mWifiInfo?.let {
            if (parseWifiSSID(it.ssid).equals(DEFAULT_SSID, ignoreCase = true)) {
                getConfiguredByNetworkID(getNetworkId())?.let { wifiConfiguration ->
                    return parseWifiSSID(wifiConfiguration.SSID)
                }
            }else{
                return parseWifiSSID(it.ssid)
            }
        }
        return DEFAULT_SSID
    }

    // 得到接入点的BSSID
    fun getBSSID(): String {
        mWifiInfo?.let {
            if(it.bssid?.isNotBlank() == true){
                return it.bssid
            }
        }
        return WifiUtil.INVALID_BSSID
    }

    //Wifi信号强度
    fun getRssi(): Int {
        mWifiInfo?.let {
            return it.rssi
        }
        return -126
    }

    //Wifi信号强度
    fun getSupplicantState(): SupplicantState {
        mWifiInfo?.let {
            return it.supplicantState
        }
        return SupplicantState.DISCONNECTED
    }

    //Wifi信号强度等级
    fun getSignalLevel(): Int {
        return WifiManager.calculateSignalLevel(getRssi(), 5)
    }

    //连接速度
    fun getLinkSpeed(): Int {
        mWifiInfo?.let {
            return it.linkSpeed
        }
        return -1
    }

    //连接速度单位
    fun getLinkSpeedUnits(): String {
        return WifiInfo.LINK_SPEED_UNITS
    }

    // 得到IP地址
    fun getIpAddress(): Int {
        mWifiInfo?.let {
            return it.ipAddress
        }
        return 0
    }

    // 得到连接的ID
    private fun getNetworkId(): Int {
        mWifiInfo?.let {
            return it.networkId
        }
        return -1
    }

    // 得到连接的ID
    private fun getFrequency(): Int {
        mWifiInfo?.let {
            return it.frequency
        }
        return 0
    }

    //判断是否是5GWiFi
    fun is5GHzWiFi(): Boolean {
        val frequency = getFrequency();
        return frequency in 4901..5899
    }

    //获取当前连接的wifi的配置信息
    fun getWifiConfiguration(): WifiConfiguration? {
        return getConfiguredByNetworkID(getNetworkId())
    }

    //wifi网络是否连接
    fun isWifiAvailable(): Boolean {
        mWiFiNetworkInfo?.let {
            return it.type == ConnectivityManager.TYPE_WIFI
        }
        return false
    }

    // 得到WifiInfo的所有信息包
    fun getInfoString(): String {
        val buffer = StringBuffer()
        mWifiInfo?.let {
            buffer.append("\n\tWifiInfo-> ").append(it.toString())
        }
        mWiFiNetworkInfo?.let {
            buffer.append("\n\tnetwork-> ").append(it.toString())
        }

        getWifiConfiguration()?.let {
            buffer.append("\n\tWifiConfiguration-> ").append(getWifiConfigurationString(it))
        }
        buffer.append("\n\twifi state-> $wifiState").append(";network state-> ${isWifiAvailable()}")
        buffer.append("\n\tmWifiList-> " + getScanResultList().size).append(";mWifiConfigurationList-> " + getConfigurationList().size)
        return buffer.toString()
    }

    fun init(context: Context, debug: Boolean) {
        init(context, debug,false)
    }

    fun init(context: Context, debug: Boolean, notifyRSSI: Boolean) {
        // 取得WifiManager对象
        mContext = context;
        isDebug = debug
        mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        mConnectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // 取得WifiInfo对象
        updateBaseInfo()
        updateConfiguredListInfo()
        updateScanListInfo()
        mWifiReceiver = NetworkBroadcastReceiver()
        //需要过滤多个动作，则调用IntentFilter对象的addAction添加新动作
        val foundFilter = IntentFilter()
        //监听Wifi当前网络状态
        foundFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        //监听Wifi硬件状态(关闭、开启、...)
        foundFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        //监听与接入点之间的连接状态(新连接建立或者现有连接丢失)
        foundFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)
        //监听Wifi发现新的接入点
        foundFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        //监听Wifi信号强度变化
        mNotifyRSSI = notifyRSSI
        if(mNotifyRSSI){
            foundFilter.addAction(WifiManager.RSSI_CHANGED_ACTION)
        }
        context.applicationContext.registerReceiver(mWifiReceiver, foundFilter)
    }


    // 打开WIFI
    fun openWifi() {
        mConnectivityManager?.activeNetworkInfo?.let {
            if (it.type == ConnectivityManager.TYPE_WIFI) {
                return
            } else {
                mWifiManager?.isWifiEnabled = true
                startScan()
            }
        }
    }

    // 关闭WIFI
    fun closeWifi() {
        if (mWifiManager?.isWifiEnabled == true) {
            mWifiManager?.isWifiEnabled = false
        }
    }

    //扫描列表
    fun startScan() {
        ThreadManager.getInstance().start { mWifiManager?.startScan() }
    }

    //更新当前连接的wifi的信息
    private fun updateBaseInfo() {
        mWifiInfo = mWifiManager?.connectionInfo
        mWiFiNetworkInfo = mConnectivityManager?.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
    }

    //更新wifi列表信息
    private fun updateScanListInfo() {
        // 得到扫描结果
        try {
            mWifiList = mWifiManager?.scanResults
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun updateConfiguredListInfo() {
        // 得到配置好的网络连接
        try {
            mWifiConfigurationList = mWifiManager?.configuredNetworks
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun checkBaseInfoNotify() {
        val temp = mWifiInfo
        updateBaseInfo()
        if (isDebug) ZLog.d(TAG + " networkId ${temp?.networkId} ;networkId ${getNetworkId()}")
        if (isDebug) ZLog.d(TAG + " SupplicantState ${temp?.supplicantState} ;SupplicantState ${mWifiInfo?.supplicantState}")
        mWifiChangeListener?.let { listener ->
            if (null != temp) {
                when {
                    temp.networkId != getNetworkId() -> // 连接新Wi-Fi
                        listener.onWifiInfoChanged()
                    temp.supplicantState != mWifiInfo?.supplicantState -> // 当前连接Wi-Fi 状态变化
                        listener.onWifiInfoChanged()
                    else -> {}
                }
            } else {
                mWifiInfo?.let {
                    listener.onWifiInfoChanged()
                }
            }
        }
    }

    private fun checkConfiguredInfoNotify() {
        val size = getConfigurationList().size
        updateConfiguredListInfo()
        if (isDebug) ZLog.d(TAG + " size $size ;size  + ${getConfigurationList().size}")
        mWifiChangeListener?.let {
            if(getConfigurationList().isNotEmpty() && getConfigurationList().size != size){
                it.onConnectUpdate(getConfigurationList())
            }
        }
    }

    @JvmOverloads
    fun connectWifi(ssid: String, password: String, type: Int, forceDeleteIfExist: Boolean = true) {
        var networkId = -1
        var tempConfig = getConfiguredBySSID(ssid)
        //如果该SSID已经连接过，而且本次连接需要强制重连，直接remove历史
        if (tempConfig != null && forceDeleteIfExist) {
            mWifiManager?.removeNetwork(tempConfig.networkId)
            tempConfig = null
        }

        networkId = if (null != tempConfig && tempConfig.networkId > -1) {
            tempConfig.networkId
        } else {
            val wcg = createWifiInfo(ssid, password, type)
            mWifiManager?.addNetwork(wcg)?:-1
        }

        connectWifi(networkId)
    }

    // 指定配置好的网络进行连接
    fun connectWifi(networkId: Int) {
        if (networkId > -1 && mWifiManager?.connectionInfo?.networkId != networkId) {
            mWifiManager?.disconnect()
            mWifiManager?.enableNetwork(networkId, true)
            mWifiManager?.reconnect()
        } else {
            mWifiChangeListener?.let {
                it.onWifiInfoChanged()
            }
        }
    }

    //Wifi的名称
    fun isCurrent(ssid: String): Boolean {
        getConfiguredBySSID(ssid)?.let {
            return it.networkId > 0 && it.networkId == getNetworkId()
        }
        return false
    }

    fun getNetType(context: Context):Int {
        var netValue = NETWORK_CLASS_UNKNOWN
        if (null == context) {
            return netValue
        }

        val info = mConnectivityManager?.activeNetworkInfo
        ZLog.d("getNetType info:$info")
        if (info == null) {
            netValue = NETWORK_CLASS_NULL
        } else if (info.type == ConnectivityManager.TYPE_WIFI) {
            netValue = NETWORK_CLASS_WIFI
        } else if (info.type == ConnectivityManager.TYPE_MOBILE) {
            netValue = NetworkUtil.getMobileNetworkClass(context, info)
        } else if (info.type == ConnectivityManager.TYPE_ETHERNET) {
            netValue = NETWORK_CLASS_ETHERNET
        } else {
            netValue = NETWORK_CLASS_UNKNOWN
        }
        ZLog.d("getNetType netValue:$netValue")
        return netValue
    }

    fun getNetTypeName(context: Context): String {
        var netValue = "unknow"
        if (null == context) {
            return netValue
        }

        val info = mConnectivityManager?.activeNetworkInfo
        if (info == null) {
            netValue = "unknow"
        } else if (info.type == ConnectivityManager.TYPE_WIFI) {
            netValue = "wifi"
        } else if (info.type == ConnectivityManager.TYPE_MOBILE) {
            netValue = "mobile"
        } else if (info.type == ConnectivityManager.TYPE_ETHERNET) {
            netValue = "ethernet"
        } else {
            netValue = "unknow"
        }
        return netValue
    }

    // 主要提供在WiFi下获取移动网络的网络类型，不保证移动网络已连接，仅是信号类型
    fun getMobileNetType(context: Context): Int {
        return NetworkUtil.getMobileNetworkClass(context, null)
    }

    fun getWiFiBssID(): String? {
        var bssId: String? = ""
        try {
            val wi = mWifiManager?.connectionInfo
            bssId = wi?.bssid // WiFi路由的MAC地址
        } catch (e: Exception) {
            ZLog.e("getWiFiBssID error:" + e.message)
        }

        return if (bssId != null && bssId.length > 0) {
            bssId.toUpperCase()
        } else bssId
    }

    fun getWifiMacAddr(): String {
        val dhcpInfo = mWifiManager?.dhcpInfo ?: return ""
        val gateWayIp = IpUtils.ipn2s(dhcpInfo.gateway)
        ZLog.d("getWifiMacAddr gateWayIp:$gateWayIp")
        return WifiUtil.getLanMacAddr(gateWayIp)
    }

    private fun getWifiConfigurationString(info: WifiConfiguration?): String {
        val sbuf = StringBuilder()
        info?.let {
            sbuf.append("SSID:").append(info.SSID)
            sbuf.append(";networkId:").append(info.networkId)
            sbuf.append(";status:").append(info.status)
            sbuf.append(";BSSID:").append(info.BSSID)
        }
        return sbuf.toString()
    }

    fun getConfiguredBySSID(ssid: String): WifiConfiguration? {
        if(TextUtils.isEmpty(ssid)){
            return null
        }
        getConfigurationList()?.let {
            if(it.isNotEmpty()){
                for (existingConfig in it) {
                    if (parseWifiSSID(existingConfig?.SSID) == ssid) {
                        return existingConfig
                    }
                }
            }
        }

        return null
    }

    private fun getConfiguredByNetworkID(networkId: Int): WifiConfiguration? {
        if (networkId < 0) {
            return null
        }
        getConfigurationList().let {
            if (it.isNotEmpty()) {
                for (existingConfig in it) {
                    if (existingConfig?.networkId == networkId) {
                        return existingConfig
                    }
                }
            }
        }

        return null
    }


    fun parseWifiSSID(ssid: String?): String {
        if (ssid.isNullOrBlank()) {
            return ""
        }
        var result = ssid
        if (result.startsWith("\"")) {
            result = result.substring(1, result.length)
        }
        if (result.endsWith("\"")) {
            result = result.substring(0, result.length - 1)
        }
        return result
    }

    private fun createWifiInfo(ssid: String, Password: String, Type: Int): WifiConfiguration {
        val config = WifiConfiguration()
        config.allowedAuthAlgorithms.clear()
        config.allowedGroupCiphers.clear()
        config.allowedKeyManagement.clear()
        config.allowedPairwiseCiphers.clear()
        config.allowedProtocols.clear()
        config.SSID = "\"" + ssid + "\""

        when (Type) {
            1 -> {
                //WIFICIPHER_NOPASS
                config.wepKeys[0] = ""
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                config.wepTxKeyIndex = 0
            }
            2 -> {
                //WIFICIPHER_WEP
                config.hiddenSSID = true
                config.wepKeys[0] = "\"" + Password + "\""
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                config.wepTxKeyIndex = 0
            }
            3 -> {
                //WIFICIPHER_WPA
                config.preSharedKey = "\"" + Password + "\""
                config.hiddenSSID = true
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.status = WifiConfiguration.Status.ENABLED
            }
        }
        return config
    }

    inner class NetworkBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == WIFI_STATE_CHANGED_ACTION) {
                if (isDebug) ZLog.d("$TAG---------------------- ")
                //上一次的wifi状态
                //                    int wifiStatePre = intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                //新的wifi状态
                wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED)
                mWifiChangeListener?.onStateChanged(wifiState)
                //处理各种wifi状态
                if (WifiManager.WIFI_STATE_ENABLED == wifiState || WifiManager.WIFI_STATE_ENABLING == wifiState) {
                    startScan()
                }
                if (isDebug) ZLog.d("$TAG WIFI_STATE_CHANGED_ACTION ${getInfoString()}")
                if (isDebug) ZLog.d("$TAG---------------------- ")
            } else if (action == WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION) {
                if (isDebug) ZLog.d("$TAG---------------------- ")
                if (isDebug) ZLog.d("$TAG SUPPLICANT_CONNECTION_CHANGE_ACTION ${getInfoString()}")
                checkBaseInfoNotify()
                if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
                    //TODO: 建立新连接
                } else {
                    //TODO: 现有连接丢失
                }

                if (isDebug) ZLog.d("$TAG---------------------- ")
            } else if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                if (isDebug) ZLog.d("$TAG---------------------- ")
                checkConfiguredInfoNotify()
                checkBaseInfoNotify()
                if (isDebug) ZLog.d("$TAG NETWORK_STATE_CHANGED_ACTION ${getInfoString()}")
                if (isDebug) ZLog.d("$TAG---------------------- ")
            } else if (action == WifiManager.RSSI_CHANGED_ACTION) {
                if (isDebug || mNotifyRSSI) {
                    if (isDebug) ZLog.d("$TAG---------------------- ")
                    updateBaseInfo()
                    if (isDebug) ZLog.d("$TAG RSSI_CHANGED_ACTION ${getInfoString()}")
                    if (isDebug) ZLog.d("$TAG---------------------- ")
                }
            } else if (action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                updateScanListInfo()
                mWifiChangeListener?.onScanUpdate(mWifiList)
            }
        }
    }

    companion object {

        @Volatile
        private var instance: WifiManagerWrapper? = null

        fun getInstance(): WifiManagerWrapper {
            if (instance == null) {
                synchronized(WifiManagerWrapper::class.java) {
                    if (instance == null) {
                        instance = WifiManagerWrapper()
                    }
                }
            }
            return instance!!
        }
    }

}
