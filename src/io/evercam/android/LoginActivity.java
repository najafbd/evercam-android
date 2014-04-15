package io.evercam.android;

import io.evercam.API;
import io.evercam.ApiKeyPair;
import io.evercam.EvercamException;
import io.evercam.User;
import io.evercam.android.dal.DbAppUser;
import io.evercam.android.dto.AppUser;
import io.evercam.android.utils.AppData;
import io.evercam.android.utils.Constants;
import io.evercam.android.utils.PrefsManager;
import io.evercam.android.utils.PropertyReader;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.bugsense.trace.BugSenseHandler;
import com.google.analytics.tracking.android.EasyTracker;

public class LoginActivity extends ParentActivity
{
	public static final int loginVerifyRequestCode = 5; 
	public static int loginResultSuccessCode = 5;

	private EditText usernameEdit;
	private EditText passwordEdit;
	private String username;
	private String password;
	private LoginTask loginTask;
	private SharedPreferences sharedPrefs;
	private String developerAppKey;
	private String developerAppID;
	private String TAG = "evercamapp-LoginActivity";
	private ProgressDialog progressDialog;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		launchBugsense();

		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.login);

		setEvercamDeveloperKeypair();

		Button btnLogin = (Button) findViewById(R.id.btnLogin);

		sharedPrefs = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putString("AppUserEmail", null);
		editor.putString("AppUserPassword", null);
		editor.commit();

		usernameEdit = (EditText) findViewById(R.id.editUsername);
		passwordEdit = (EditText) findViewById(R.id.editPassword);

		btnLogin.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v)
			{
				attemptLogin();
			}
		});

	}

	public void attemptLogin()
	{
		if (loginTask != null)
		{
			return;
		}

		usernameEdit.setError(null);
		passwordEdit.setError(null);

		username = usernameEdit.getText().toString();
		password = passwordEdit.getText().toString();

		boolean cancel = false;
		View focusView = null;

		if (TextUtils.isEmpty(password))
		{
			passwordEdit.setError(getString(R.string.error_password_required));
			focusView = passwordEdit;
			cancel = true;
		}
		else if (password.contains(" "))
		{
			passwordEdit.setError(getString(R.string.error_invalid_password));
			focusView = passwordEdit;
			cancel = true;
		}

		if (TextUtils.isEmpty(username))
		{
			usernameEdit.setError(getString(R.string.error_username_required));
			focusView = usernameEdit;
			cancel = true;
		}
		else if (username.contains("@"))
		{
			usernameEdit.setError(getString(R.string.please_use_username));
			focusView = usernameEdit;
			cancel = true;
		}
		else if (username.contains(" "))
		{
			usernameEdit.setError(getString(R.string.error_invalid_username));
			focusView = usernameEdit;
			cancel = true;
		}

		if (cancel)
		{
			focusView.requestFocus();
		}
		else
		{
			showProgressDialog();
			loginTask = new LoginTask();
			loginTask.execute();
		}
	}

	public class LoginTask extends AsyncTask<Void, Void, Boolean>
	{
		private String errorMessage = "LoginTaskMessage";
		private AppUser newUser = null;

		@Override
		protected Boolean doInBackground(Void... params)
		{
			try
			{
				ApiKeyPair userKeyPair = API.requestUserKeyPairFromEvercam(username, password);
				String userApiKey = userKeyPair.getApiKey();
				String userApiId = userKeyPair.getApiId();
				API.setUserKeyPair(userApiKey, userApiId);
				User evercamUser = new User(username);
				newUser = new AppUser();
				newUser.setUsername(username);
				newUser.setPassword(password);
				newUser.setIsDefault(true);
				newUser.setCountry(evercamUser.getCountry());
				newUser.setEmail(evercamUser.getEmail());
				newUser.setApiKey(userApiKey);
				newUser.setApiId(userApiId);
				return true;
			}
			catch (EvercamException e)
			{
				errorMessage = e.getMessage();
			}
			return false;
		}

		@Override
		protected void onPostExecute(final Boolean success)
		{
			loginTask = null;
			dismissProgressDialog();

			if (success)
			{
				DbAppUser dbUser = new DbAppUser(LoginActivity.this);

				if (dbUser.getAppUserByUsername(newUser.getUsername()) != null)
				{
					dbUser.deleteAppUserByUsername(newUser.getUsername());
				}
				dbUser.updateAllIsDefaultFalse();

				dbUser.addAppUser(newUser);
				AppData.defaultUser = newUser;
				PrefsManager.saveUserEmail(sharedPrefs, newUser.getEmail());
				finishLoginActivity();

				// AppData.camesList = new ArrayList<Camera>(); 
			}
			else
			{
				Toast toast = Toast.makeText(getApplicationContext(), errorMessage,
						Toast.LENGTH_SHORT);
				toast.setGravity(Gravity.CENTER, 0, 0);
				toast.show();
				passwordEdit.setText(null);
			}
		}

		@Override
		protected void onCancelled()
		{
			loginTask = null;
			dismissProgressDialog();
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (Constants.isAppTrackingEnabled)
		{
			EasyTracker.getInstance().activityStart(this);
			if (Constants.isAppTrackingEnabled)
				{BugSenseHandler.startSession(this);
				}
		}
	}

	@Override
	public void onStop()
	{
		super.onStop();

		if (Constants.isAppTrackingEnabled)
		{
			EasyTracker.getInstance().activityStop(this);
			if (Constants.isAppTrackingEnabled)
				{BugSenseHandler.closeSession(this);
				}
		}
	}

	private void launchBugsense()
	{
		if (Constants.isAppTrackingEnabled)
		{
			BugSenseHandler.initAndStartSession(this, Constants.bugsense_ApiKey);
		}
	}

	private void showProgressDialog()
	{
		progressDialog = new ProgressDialog(LoginActivity.this);
		progressDialog.setMessage(getString(R.string.login_progress_signing_in));
		progressDialog.setCanceledOnTouchOutside(false); // can not be canceled
		progressDialog.show();
	}

	private void dismissProgressDialog()
	{
		if (progressDialog != null && progressDialog.isShowing())
		{
			progressDialog.dismiss();
		}
	}

	private void setEvercamDeveloperKeypair()
	{
		PropertyReader propertyReader = new PropertyReader(getApplicationContext());
		developerAppKey = propertyReader.getPropertyStr(PropertyReader.KEY_API_KEY);
		developerAppID = propertyReader.getPropertyStr(PropertyReader.KEY_API_ID);
		API.setDeveloperKeyPair(developerAppKey, developerAppID);
	}

	private void finishLoginActivity()
	{
		setResult(loginResultSuccessCode);
		this.finish();
	}
}