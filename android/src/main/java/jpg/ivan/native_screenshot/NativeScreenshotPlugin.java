package jpg.ivan.native_screenshot;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Date;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.embedding.engine.renderer.FlutterRenderer;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterView;

/**
 * NativeScreenshotPlugin
 */
public class NativeScreenshotPlugin implements MethodCallHandler, FlutterPlugin, ActivityAware {
	private static final String TAG = "NativeScreenshotPlugin";

	private Context context;
	private MethodChannel channel;
	private Activity activity;
	private Object renderer;

	private boolean ssError = false;
	private String ssPath;

	// Default constructor for old registrar
	public NativeScreenshotPlugin() {
	} // NativeScreenshotPlugin()

	// Condensed logic to initialize the plugin
	private void initPlugin(Context context, BinaryMessenger messenger, Activity activity, Object renderer) {
		this.context = context;
		this.activity = activity;
		this.renderer = renderer;

		this.channel = new MethodChannel(messenger, "native_screenshot");
		this.channel.setMethodCallHandler(this);
	} // initPlugin()

	// New v2 listener methods
	@Override
	public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
		this.channel.setMethodCallHandler(null);
		this.channel = null;
		this.context = null;
	} // onDetachedFromEngine()

	@Override
	public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
		Log.println(Log.INFO, TAG, "Using *NEW* registrar method!");

		initPlugin(
				flutterPluginBinding.getApplicationContext(),
				flutterPluginBinding.getBinaryMessenger(),
				null,
				flutterPluginBinding.getFlutterEngine().getRenderer()); // initPlugin()
	} // onAttachedToEngine()

	// Old v1 register method
	// FIX: Make instance variables set with the old method
	public static void registerWith(Registrar registrar) {
		Log.println(Log.INFO, TAG, "Using *OLD* registrar method!");

		NativeScreenshotPlugin instance = new NativeScreenshotPlugin();

		instance.initPlugin(
				registrar.context(),
				registrar.messenger(),
				registrar.activity(),
				registrar.view()); // initPlugin()
	} // registerWith()

	// Activity condensed methods
	private void attachActivity(ActivityPluginBinding binding) {
		this.activity = binding.getActivity();
	} // attachActivity()

	private void detachActivity() {
		this.activity = null;
	} // attachActivity()

	// Activity listener methods
	@Override
	public void onAttachedToActivity(ActivityPluginBinding binding) {
		attachActivity(binding);
	} // onAttachedToActivity()

	@Override
	public void onDetachedFromActivityForConfigChanges() {
		detachActivity();
	} // onDetachedFromActivityForConfigChanges()

	@Override
	public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
		attachActivity(binding);
	} // onReattachedToActivityForConfigChanges()

	@Override
	public void onDetachedFromActivity() {
		detachActivity();
	} // onDetachedFromActivity()

	// MethodCall, manage stuff coming from Dart
	@Override
	public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
		if (!permissionToWrite()) {
			Log.println(Log.INFO, TAG, "Permission to write files missing!");

			result.success(null);

			return;
		} // if cannot write

		if (!call.method.equals("takeScreenshot")) {
			Log.println(Log.INFO, TAG, "Method not implemented!");

			result.notImplemented();

			return;
		} // if not implemented

		// Need to fix takeScreenshot()
		// it produces just a black image
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// takeScreenshot();
			takeScreenshotOld();
		} else {
			takeScreenshotOld();
		} // if

		if (this.ssError || this.ssPath == null || this.ssPath.isEmpty()) {
			result.success(null);

			return;
		} // if error

		result.success(this.ssPath);
	} // onMethodCall()

	// Own functions, plugin specific functionality
	private String getScreenshotName() {
		java.text.SimpleDateFormat sf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
		String sDate = sf.format(new Date());

		return "native_screenshot-" + sDate + ".png";
	} // getScreenshotName()

	private String getApplicationName() {
		ApplicationInfo appInfo = null;

		try {
			appInfo = this.context.getPackageManager()
					.getApplicationInfo(this.context.getPackageName(), 0);
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error getting package name, using default. Err: " + ex.getMessage());
		}

		if (appInfo == null) {
			return "NativeScreenshot";
		} // if null

		CharSequence cs = this.context.getPackageManager().getApplicationLabel(appInfo);
		StringBuilder name = new StringBuilder(cs.length());

		name.append(cs);

		if (name.toString().trim().isEmpty()) {
			return "NativeScreenshot";
		}

		return name.toString();
	} // getApplicationName()

	private String getScreenshotPath() {
		// String externalDir =
		// Environment.getExternalStorageDirectory().getAbsolutePath();
		String externalDir = this.activity.getExternalFilesDir(Environment.DIRECTORY_DCIM).getAbsolutePath();

		String sDir = externalDir
				+ File.separator
				+ getApplicationName();

		File dir = new File(sDir);

		String dirPath;

		if (dir.exists() || dir.mkdir()) {
			dirPath = sDir + File.separator + getScreenshotName();
		} else {
			dirPath = externalDir + File.separator + getScreenshotName();
		}

		Log.println(Log.INFO, TAG, "Built ScreeshotPath: " + dirPath);

		return dirPath;
	} // getScreenshotPath()

	private String writeBitmap(Bitmap bitmap) {
		final String relPath = getApplicationName() + File.separator + getScreenshotName();
		final ContentValues values = new ContentValues();
		values.put(MediaStore.MediaColumns.DISPLAY_NAME, relPath);
		values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
		values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);

		final ContentResolver resolver = this.context.getContentResolver();
		Uri uri = null;

		try {
			uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

			if (uri == null)
				throw new IOException("Failed to create new MediaStore record");

			final OutputStream stream = resolver.openOutputStream(uri);

			if (stream == null)
				throw new IOException("Failed to open output stream to MediaStore");

			if (!bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream))
				throw new IOException("Failed to save bitmap to MediaStore");

			// String path = getScreenshotPath();
			// File imageFile = new File(path);
			// FileOutputStream oStream = new FileOutputStream(imageFile);

			// bitmap.compress(Bitmap.CompressFormat.PNG, 100, oStream);
			// oStream.flush();
			// oStream.close();

			return getPath(this.context, uri);
		} catch (Exception ex) {
			if (uri != null)
				resolver.delete(uri, null, null);

			Log.println(Log.INFO, TAG, "Error writing bitmap: " + ex.getMessage());
		}

		return null;
	} // writeBitmap()

	private void reloadMedia() {
		try {
			Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			File file = new File(this.ssPath);
			Uri uri = Uri.fromFile(file);

			intent.setData(uri);
			this.activity.sendBroadcast(intent);
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error reloading media lib: " + ex.getMessage());
		}
	} // reloadMedia()

	private void takeScreenshot() {
		Log.println(Log.INFO, TAG, "Trying to take screenshot [new way]");

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			this.ssPath = null;
			this.ssError = true;

			return;
		}

		try {
			Window window = this.activity.getWindow();
			View view = this.activity.getWindow().getDecorView().getRootView();

			Bitmap bitmap = Bitmap.createBitmap(
					view.getWidth(),
					view.getHeight(),
					Bitmap.Config.ARGB_8888); // Bitmap()

			Canvas canvas = new Canvas(bitmap);
			view.draw(canvas);

			// int[] windowLocation = new int[2];
			// view.getLocationInWindow(windowLocation);
			//
			// PixelListener listener = new PixelListener();
			//
			// PixelCopy.request(
			// window,
			// new Rect(
			// windowLocation[0],
			// windowLocation[1],
			// windowLocation[0] + view.getWidth(),
			// windowLocation[1] + view.getHeight()
			// ),
			// bitmap,
			// listener,
			// new Handler()
			// ); // PixelCopy.request()
			//
			// if( listener.hasError() ) {
			// this.ssError = true;
			// this.ssPath = null;
			//
			// return;
			// } // if error

			String path = writeBitmap(bitmap);
			if (path == null || path.isEmpty()) {
				this.ssPath = null;
				this.ssError = true;
			} // if no path

			this.ssError = false;
			this.ssPath = path;

			reloadMedia();
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error taking screenshot: " + ex.getMessage());
		}
	} // takeScreenshot()

	private void takeScreenshotOld() {
		Log.println(Log.INFO, TAG, "Trying to take screenshot [old way]");

		try {
			View view = this.activity.getWindow().getDecorView().getRootView();

			view.setDrawingCacheEnabled(true);

			Bitmap bitmap = null;
			if (this.renderer.getClass() == FlutterView.class) {
				bitmap = ((FlutterView) this.renderer).getBitmap();
			} else if (this.renderer.getClass() == FlutterRenderer.class) {
				bitmap = ((FlutterRenderer) this.renderer).getBitmap();
			}

			if (bitmap == null) {
				this.ssError = true;
				this.ssPath = null;

				Log.println(Log.INFO, TAG, "The bitmap cannot be created :(");

				return;
			} // if

			view.setDrawingCacheEnabled(false);

			String path = writeBitmap(bitmap);
			if (path == null || path.isEmpty()) {
				this.ssError = true;
				this.ssPath = null;

				Log.println(Log.INFO, TAG, "The bitmap cannot be written, invalid path.");

				return;
			} // if

			this.ssError = false;
			this.ssPath = path;

			reloadMedia();
		} catch (Exception ex) {
			Log.println(Log.INFO, TAG, "Error taking screenshot: " + ex.getMessage());
		}
	} // takeScreenshot()

	private boolean permissionToWrite() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			Log.println(Log.INFO, TAG, "Permission to write false due to version codes.");

			return false;
		}

		int perm = this.activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);

		if (perm != PackageManager.PERMISSION_GRANTED) {
			Log.println(Log.INFO, TAG, "Requesting permissions...");
			this.activity.requestPermissions(
					new String[] {
							Manifest.permission.READ_EXTERNAL_STORAGE,
							Manifest.permission.WRITE_EXTERNAL_STORAGE
					},
					11); // requestPermissions()

			perm = this.activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}

		if (perm == PackageManager.PERMISSION_GRANTED) {
			Log.println(Log.INFO, TAG, "Permission to write granted!");

			return true;
		} // if

		Log.println(Log.INFO, TAG, "No permissions :(");

		return false;
	} // permissionToWrite()

	// CODE BELOW IS AN EXCERP FROM
	// https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.<br>
	 * <br>
	 * Callers should check whether the path is local before assuming it
	 * represents a local file.
	 * 
	 * @param context The context.
	 * @param uri     The Uri to query.
	 * @see #isLocal(String)
	 * @see #getFile(Context, Uri)
	 * @author paulburke
	 */
	public static String getPath(final Context context, final Uri uri) {

		// if (DEBUG)
		// Log.d(TAG + " File -",
		// "Authority: " + uri.getAuthority() +
		// ", Fragment: " + uri.getFragment() +
		// ", Port: " + uri.getPort() +
		// ", Query: " + uri.getQuery() +
		// ", Scheme: " + uri.getScheme() +
		// ", Host: " + uri.getHost() +
		// ", Segments: " + uri.getPathSegments().toString());

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// // LocalStorageProvider
			// if (isLocalStorageDocument(uri)) {
			// // The path is the id
			// return DocumentsContract.getDocumentId(uri);
			// }
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 * @author paulburke
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 * @author paulburke
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 * @author paulburke
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context       The context.
	 * @param uri           The Uri to query.
	 * @param selection     (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 * @author paulburke
	 */
	public static String getDataColumn(Context context, Uri uri, String selection,
			String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				// if (DEBUG)
				// DatabaseUtils.dumpCursor(cursor);

				final int column_index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(column_index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}
} // NativeScreenshotPlugin
