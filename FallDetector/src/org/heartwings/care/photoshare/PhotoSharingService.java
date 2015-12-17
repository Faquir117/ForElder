package org.heartwings.care.photoshare;

import java.net.InetSocketAddress;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.heartwings.care.Constants;
import org.heartwings.care.MainActivity;
import org.heartwings.care.position.PhonePositionHelper;
import org.heartwings.network.ClientHandler;
import org.heartwings.network.HeartMessage;
import org.heartwings.network.NetworkChecker;

import com.alibaba.fastjson.JSON;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * @author Inno520 ���񣺵��е���Ƭ�ʹ��ʱ���Զ����ص������
 */
public class PhotoSharingService extends Service {
	private NioSocketConnector connector;
	private IoSession session;
	private ClientHandler clientHandler;

	private HandlerThread noticeThread;
	private DownloadResultHandler noticeHandler;
	private Thread workerThread;
	private PowerManager powerManager;
	private boolean started = false;
	private WakeLock wakeLock;
	private static PhotoSharingService photoSharingService = null;

	private static class DownloadResultHandler extends Handler {
		public DownloadResultHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Constants.MESSAGE_DOWNLOAD_SUCCESS:
				Toast.makeText(
						MainActivity.getMainActivity().getApplicationContext(),
						"�������", Toast.LENGTH_SHORT).show();
				break;
			case Constants.MESSAGE_DOWNLOAD_FAILED:
				Toast.makeText(
						MainActivity.getMainActivity().getApplicationContext(),
						"����ʧ��", Toast.LENGTH_SHORT).show();
				break;
			case Constants.MESSAGE_BIND_REQUEST:
				Pair<HeartMessage, IoSession> pair = (Pair<HeartMessage, IoSession>) msg.obj;
				Log.i("Bind",
						"Bind request from " + pair.first.getFromUserName());
				final HeartMessage heartMessage = pair.first;
				final IoSession session = pair.second;
				AlertDialog confirmDialog = new AlertDialog.Builder(
						MainActivity.getTheApplicationContext())
						.setTitle("�󶨹���")
						.setMessage("�û���" + heartMessage.getFromUserName())
						.setPositiveButton("��",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										dialog.dismiss();
										Log.i("Bind",
												"Accept the bind request.");
										HeartMessage msg = new HeartMessage();
										msg.setCreateTime(System
												.currentTimeMillis());
										msg.setFromUserName(heartMessage
												.getToUserName());
										msg.setToUserName(heartMessage
												.getFromUserName());
										msg.setMsgType("returnbind");
										msg.setMsgContent("1");

										session.write(JSON.toJSONString(msg));
									}
								})
						.setNegativeButton("�ܾ�",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										dialog.dismiss();
										Log.i("Bind",
												"Refused the bind request.");
										HeartMessage msg = new HeartMessage();
										msg.setCreateTime(System
												.currentTimeMillis());
										msg.setFromUserName(heartMessage
												.getToUserName());
										msg.setToUserName(heartMessage
												.getFromUserName());
										msg.setMsgType("returnbind");
										msg.setMsgContent("0");
										session.write(JSON.toJSONString(msg));
									}
								}).create();
				confirmDialog.getWindow().setType(
						WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
				confirmDialog.setCancelable(false);
				confirmDialog.setCanceledOnTouchOutside(false);
				confirmDialog.show();
				break;
			}
			super.handleMessage(msg);
		}
	}

	public PhotoSharingService() {
	}

	public static PhotoSharingService getPhotoSharingService() {
		return photoSharingService;
	}

	public Handler getNoticeHandler() {
		return noticeHandler;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@SuppressLint("NewApi")
	@Override
	public void onCreate() {
		Log.d("PhotoShare", "Start to create service");
		photoSharingService = this;
		// Connections
		clientHandler = new ClientHandler();
		connector = new NioSocketConnector();
		connector.setConnectTimeoutMillis(10 * 1000);
		/* connector.getFilterChain().addLast("logger", new LoggingFilter()); */
		connector.getFilterChain().addLast("codec",
				new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
		clientHandler = new ClientHandler();
		connector.setHandler(clientHandler);

		// Aquire lock
		powerManager = (PowerManager) getApplicationContext().getSystemService(
				Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"MyWakeLock");

		// Set the service as ForegroundService
		Notification notification = new Notification.Builder(this).build();
		startForeground(1315, notification);

		Log.d("PhotoShare", "Service created..");
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		PhonePositionHelper.getPhonePositionHelper().stopGathering();
		Log.i("PhotoShare", "Service Destroyed");
		started = false;
		photoSharingService = null;
		wakeLock.release();
		stopForeground(true);
		session.close(true);
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("PhotoShare", "Starting service");
		if(NetworkChecker.isNetAvailable(this))
		{
			Log.d("PhotoShare","Network fine.");
		}else
		{
			Log.e("PhotoShare", "Cannot proceed: Network unconnected");
		}
		if (!started) {
			started = true;
			wakeLock.acquire();
			final String username = intent.getStringExtra("username");
			final String password = intent.getStringExtra("password");
			if (username == null || password == null) {
				Log.d("PhotoShare", "Need Username and Password");
				return super.onStartCommand(intent, flags, startId);
			}
			// �����������ؽ�����߳�
			noticeThread = new HandlerThread("NoticeThread");
			noticeThread.start();
			noticeHandler = new DownloadResultHandler(noticeThread.getLooper());

			// ���Դ������ؽ����֮�󣬿�ʼ��������
			workerThread = new Thread(new Runnable() {
				@Override
				public void run() {
					ConnectFuture future = connector.connect(new InetSocketAddress(
							Constants.PHOTO_SHARE_SERVER_ADDRESS,
							Constants.PHOTO_SHARE_SERVER_PORT));
					future.awaitUninterruptibly();
					session = future.getSession();
					session.setAttribute("thisClient", username);
					if (session.isConnected()) {
						Log.i("PhotoShare",
								"The connection to server has been setablished.");
						session.getCloseFuture().awaitUninterruptibly();
					} else {
						Log.e("PhotoShare", "Connection failed.");
					}
				}
			}, "PhotoSharingThread");
			workerThread.start();

			// ����λ�ø���
			PhonePositionHelper.getPhonePositionHelper().startGathering();

			Log.d("PhotoShare", "Started service with user " + username);
		}

		return super.onStartCommand(intent, START_STICKY, startId);
	}
}
