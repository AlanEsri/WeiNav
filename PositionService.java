package edu.geosis.navtong.position;

import java.util.ArrayList;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.BDNotifyListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import edu.geosis.navtong.basis.NavTongConstant;
import edu.geosis.navtong.basis.NavTongSystemSet;
import edu.geosis.navtong.database.NavLocation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class PositionService extends Service {
	private PositionBinder positionBinder;
	private NavLocation navLocation;
	private LocationClient bdLocationClient;
	private LocationClientOption locationClientOption;
	private LocationManager locationManager;
	private LocationListener locationListener;
	private boolean isPositioningStarted;
	private boolean isNewLocation;
	private boolean isLocationAvailable;
	private int positioningMode;
	private int positioningInterval;
	
//	private Timer recordTrackTimer;
	
	public static final int GET_LOCATION = 61;
	public static final int GPS_DISABLED = 62;
	public static final int NETWORK_DISABLED = 63;
	public static final int POSITION_ERROR_BAIDU_SDK = 69;
	
	public static final String EXTRA_NAME_POSITION_STATE = "positionState";
	public static final String EXTRA_NAME_POSITION_STRING = "positionString";
	
	public static final int TRACK_RECORD_PERIOD = 10000;

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d("Alan", "PositionService is Binded!");
		return positionBinder;
	}

	public void onCreate() {
		super.onCreate();

		positionBinder = new PositionBinder();
		navLocation = new NavLocation();
		NavTongSystemSet navTongSystemSet = new NavTongSystemSet(this);
		positioningMode = navTongSystemSet.getPositioningMode();
		isPositioningStarted = false;
		positioningInterval = navTongSystemSet.getPositioningInterval();
		initializePositioningService();
		initializeBDPositioningService();
		checkGPSandNetworkStatus();
		
		Log.d("Alan", "PositionService is Created!");
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		Log.d("Alan", "PositionService is Started!");
		return Service.START_NOT_STICKY;
	}
	
	public void onDestroy() {
		super.onDestroy();
		Log.d("Alan", "PositionService is Destoryed!");
	}
	
	public boolean onUnbind(Intent intent) {
		super.onUnbind(intent);
		Log.d("Alan", "PositionService is Unbinded!");
		return true;
	}
	
	public void Rebind(Intent intent) {
		super.onRebind(intent);
		Log.d("Alan", "PositionService is Rebinded!");
	}
	
	private void initializeBDPositioningService() {
		bdLocationClient = new LocationClient(this);
		NavBDLocationListener navBDLocationListenner = new NavBDLocationListener();
		bdLocationClient.registerLocationListener(navBDLocationListenner);
		NotifyLister mNotifyer = new NotifyLister();
		mNotifyer.SetNotifyLocation(40.000725, 116.311302, 80, "bd09ll");  //北大西门经纬度
		//四个参数代表要位置提醒的点的坐标，具体含义为：纬度、经度、距离范围，坐标类型（gcj02,gps,bd09,bd09ll）
		bdLocationClient.registerNotify(mNotifyer);
		
		locationClientOption = new LocationClientOption();
		locationClientOption.setOpenGps(true);  //打开gps
		//option.setAddrType("all");  //返回的定位结果包含地址信息，其他值都表示不返回地址信息
		locationClientOption.setAddrType("noaddrback");
		locationClientOption.setCoorType("bd09ll");  //返回的定位结果是百度经纬度
		locationClientOption.setServiceName("edu.geosis.navtong");  //设置产品线名称
		locationClientOption.setScanSpan(positioningInterval);  //设置发起定位请求的间隔时间，单位为ms
		locationClientOption.disableCache(true);  //不禁止启用缓存定位
		locationClientOption.setPoiNumber(10);  //最多返回10个POI个数
		locationClientOption.setPoiDistance(1000);  //POI查询距离
		locationClientOption.setPoiExtraInfo(false);  //设置是否需要POI的电话和地址等详细信息
		locationClientOption.setPriority(LocationClientOption.NetWorkFirst);  //设置网络优先
		//option.setPriority(LocationClientOption.GpsFirst);  //不设置，默认是GPS优先
		bdLocationClient.setLocOption(locationClientOption);
	}
	
	private void initializePositioningService() {
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		locationListener = new LocationListener() {
			
			@Override
			public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onProviderEnabled(String arg0) {
			}
			
			@Override
			public void onProviderDisabled(String provider) {
				isLocationAvailable = false;
				if (LocationManager.GPS_PROVIDER.equals(provider)) {
					broadcastPositionState(GPS_DISABLED);
				}
			}
			
			@Override
			public void onLocationChanged(Location location) {
				if (location == null) {
					return;
				}
				if (location.getLatitude() == 0) {
					return;
				}
				if (location.getLongitude() == 0) {
					return;
				}
				isLocationAvailable = true;
				if (!navLocation.equals(location)) {
					isNewLocation = true;
					navLocation.adapter(location);
				}
				broadcastPositionState(GET_LOCATION);
			}
		};
	}
	
	private void checkGPSandNetworkStatus() {
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			broadcastPositionState(GPS_DISABLED);
		}
		
		ConnectivityManager connectivityManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		if (networkInfo == null) {
			broadcastPositionState(NETWORK_DISABLED);
		}
	}
	
	private void broadcastPositionState(int pState) {
		Intent intent = new Intent();
		intent.setAction(NavTongConstant.ACTION_POSITION_RESULT);
		intent.putExtra(EXTRA_NAME_POSITION_STATE, pState);
		if (pState == GET_LOCATION) {
			intent.putExtra(EXTRA_NAME_POSITION_STRING, navLocation.toString());
		}
		sendOrderedBroadcast(intent, null);
	}
	
	public class PositionBinder extends Binder {
		/**
		 * Set the positioning Mode of the Service.<br>
		 * The positioning Mode can be set ORIGINAL_MODE or BAIDU_SDK mode.<br>
		 * ORIGINAL_MODE is mainly using GPS module to get location.<br>
		 * It's precise but need some time to get location.<br>
		 * BAIDU_SDK is first connecting to Baidu server to get location.<br>
		 * It's so fast that the first positioning delay is usually less than 1 s.<br>
		 * But the location includes Baidu Offset so that you should using Baidu Map to show the location precisely.<br>
		 * <p><h1>Note:</h1> the positioning would be automatically restarted during the function be called.<br>
		 * <br>
		 * @param positioning_Mode
		 * <p>NavTongSystemSet.ORIGINAL_MODE : Positioning using original API in Android SDK.<br>
		 * NavTongSystemSet.BAIDU_SDK : Positioning using Baidu SDK.
		 * @return void
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public void setPositioningMode(int positioning_Mode){
			if (isPositioningStarted) {
				stopPositioning();
				positioningMode = positioning_Mode;
				startPositioning();
			}else {
				positioningMode = positioning_Mode;
			}
		}
		
		/**
		 * Start the positioning.<br>
		 * The location date would be stored in the NavLocation, which can be got by getNavLocation() function.<br>
		 * When the location is changed, the service would send a broadcast to notify.<br>
		 * If the positioning is started, the function is invalid.<br>
		 * @param void
		 * @return void
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public void startPositioning() {
			if (!isPositioningStarted) {
				switch (positioningMode) {
				case NavTongSystemSet.PMODE_ORIGINAL:
					ArrayList<String> providers = (ArrayList<String>)locationManager.getAllProviders();
					for (String provider : providers) {
						locationManager.requestLocationUpdates(provider, positioningInterval, 0, locationListener);  //每隔positioningInterval毫秒更新位置，不考虑距离变化
						Log.d("Alan", provider + " is started to get location.");
					}
					break;
					
				case NavTongSystemSet.PMODE_BAIDU_SDK:
					bdLocationClient.start();
					bdLocationClient.requestLocation();
					break;

				default:
					break;
				}
				isPositioningStarted = true;
			}
		}
		
		/**
		 * Stop the positioning.
		 * @param void
		 * @return void
		 * @since 2014-04-17
		 * @author Huaiyu Li
		 */
		public void stopPositioning() {
			if(isPositioningStarted) {
				switch (positioningMode) {
				case NavTongSystemSet.PMODE_ORIGINAL:
					locationManager.removeUpdates(locationListener);
					break;
					
				case NavTongSystemSet.PMODE_BAIDU_SDK:
					bdLocationClient.stop();
					break;

				default:
					break;
				}
				isPositioningStarted = false;
			}
		}
		
		/**
		 * Get the current location.<br>
		 * <h1>Note:</h1>If current location is unavailable, null would be returned.<br>
		 * @param void
		 * @return NavLocation
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public NavLocation getNavLocation() {
			if (isLocationAvailable) {
				isNewLocation = false;
				return navLocation.clone();
			}
			return null;     
		}
		
		/**
		 * Check if the positioning is started.
		 * @param void
		 * @return true - the positioning is started; false - the positioning is stopped.
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public boolean isPositioningStarted() {
			return isPositioningStarted;
		}
		
		/**
		 * Check if the current location is available.
		 * @param void
		 * @return true - current location is available; false - unavailable.
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public boolean isLocationAvailable() {
			return isLocationAvailable;
		}
		
		/**
		 * Check if the current location is different from last one.
		 * @param void
		 * @return true - the current location is new; false - not new.
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public boolean isNewLocation() {
			return isNewLocation;
		}
		
		/**
		 * Set the positioning interval.<br>
		 * <p><h1>Note:</h1> the positioning would be automatically restarted during the function be called.<br>
		 * @param positioning_Interval the positioning interval with the millisecond.
		 * @return void
		 * @since 2014-04-16
		 * @author Huaiyu Li
		 */
		public void setPositioningInterval(int positioning_Interval) {
			if (isPositioningStarted) {
				stopPositioning();
				locationClientOption.setScanSpan(positioning_Interval);
				positioningInterval = positioning_Interval;
				startPositioning();
			}else {
				locationClientOption.setScanSpan(positioning_Interval);
				positioningInterval = positioning_Interval;
			}
		}
		
		public void startRecordTrack() {
			
		}
		
		public void stopRecordTrack() {
			
		}
	}
	
	private class NavBDLocationListener implements BDLocationListener {
		public void onReceiveLocation(BDLocation location) {
			if (location == null) {
				return ;
			}
			int errorCode = location.getLocType();
			switch (errorCode) {
			case 61:  //By GPS
				isLocationAvailable = true;
				if (!navLocation.equals(location)) {
					isNewLocation = true;
					navLocation.adapter(location);
				}
				broadcastPositionState(GET_LOCATION);
				break;
				
			case 161:  //By Network
				isLocationAvailable = true;
				if (!navLocation.equals(location)) {
					isNewLocation = true;
					navLocation.adapter(location);
				}
				broadcastPositionState(GET_LOCATION);
				break;
				
			case 63:
				Log.d("Alan", "Baidu Position SDK connect to server error!");
				broadcastPositionState(NETWORK_DISABLED);
//				Toast.makeText(NavTongApplication.getInstance().getApplicationContext(), "网络异常！没有成功向服务器发起请求！请检查网络连接。", Toast.LENGTH_LONG).show();
				break;

			default:
				Log.d("Alan", "Baidu Position SDK errorCode = " + errorCode);
				broadcastPositionState(POSITION_ERROR_BAIDU_SDK);
				break;
			}
		}
		
		public void onReceivePoi(BDLocation poiLocation) {
			if (poiLocation == null){
				return ; 
			}
			//override
		}
	}
	
	private class NotifyLister extends BDNotifyListener{
		public void onNotify(BDLocation mlocation, float distance){
			//override
		}
	}
}
