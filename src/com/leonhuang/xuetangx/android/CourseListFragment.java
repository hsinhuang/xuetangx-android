package com.leonhuang.xuetangx.android;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import com.leonhuang.xuetangx.Courses;
import com.leonhuang.xuetangx.R;
import com.leonhuang.xuetangx.android.model.UserInfo;
import com.leonhuang.xuetangx.android.util.NetworkConnectivityManager;
import com.leonhuang.xuetangx.android.util.SignInStatusManager;
import com.leonhuang.xuetangx.data.SimpleCourseInfo;
import com.leonhuang.xuetangx.data.SimpleCourseStatus;
import com.renn.rennsdk.RennClient;
import com.renn.rennsdk.RennClient.LoginListener;
import com.renn.rennsdk.RennExecutor.CallBack;
import com.renn.rennsdk.RennResponse;
import com.renn.rennsdk.exception.RennException;
import com.renn.rennsdk.param.PutShareUrlParam;

public class CourseListFragment extends ListFragment {
	public static final String COURSE_STATUS = "com.leonhuang.xuetangx.android.CourseListFragment.CourseStatus";

	public static final String CACHE_COURSES_UPCOMING = "com.leonhuang.xuetangx.android.CourseListFragment.CourseCache.Upcoming";
	public static final String CACHE_COURSES_CURRENT = "com.leonhuang.xuetangx.android.CourseListFragment.CourseCache.Current";
	public static final String CACHE_COURSES_PAST = "com.leonhuang.xuetangx.android.CourseListFragment.CourseCache.Past";

	private static final String RENREN_APP_ID = "267586";
	private static final String RENREN_API_KEY = "89137a23b30d4a9d9b1acd8c0faaba40";
	private static final String RENREN_SECRET_KEY = "7e611e75794e474a98f12c872d731ba5";

	private static final int RENREN_ID = 0;

	private SwipeRefreshLayout mSwipeRefreshLayout;
	private SimpleCourseStatus courseStatus;
	private CourseAdapter adapter;
	private ListView listView;
	private final ArrayList<SimpleCourseInfo> mListItems = new ArrayList<SimpleCourseInfo>();

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		Log.i("CourseListFragment", "onCreateView");
		Bundle args = getArguments();
		courseStatus = (SimpleCourseStatus) args.getSerializable(COURSE_STATUS);

		mSwipeRefreshLayout = (SwipeRefreshLayout) inflater.inflate(
				R.layout.fragment_course_list, container, false);
		Log.i("Layout", String.valueOf(mSwipeRefreshLayout));

		mSwipeRefreshLayout.setColorScheme(R.color.holo_green_dark,
				R.color.holo_orange_dark, R.color.holo_blue_bright,
				R.color.holo_red_dark);

		listView = (ListView) mSwipeRefreshLayout
				.findViewById(android.R.id.list);
		adapter = new CourseAdapter(getActivity(), mListItems);

		new GetDataTask(new Runnable() {

			@Override
			public void run() {
				listView.setAdapter(adapter);
			}
		}).execute();

		mSwipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
			@Override
			public void onRefresh() {
				Log.i("Refresh", String.valueOf(courseStatus));
				new GetDataTask(new Runnable() {

					@Override
					public void run() {
						adapter.notifyDataSetInvalidated();
					}
				}).execute();
			}
		});

		return mSwipeRefreshLayout;
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		switch (courseStatus) {
		case UPCOMING:

			break;
		case CURRENT:
		case PAST:
			Log.i("URL", String.valueOf(position));
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mListItems
					.get(position).getCourseInfoUrl()));
			startActivity(intent);
			break;
		default:
			break;
		}
	}

	private void shareToRenren(final String comment, final String url) {
		final RennClient rennClient = RennClient.getInstance(getActivity());
		rennClient.init(RENREN_APP_ID, RENREN_API_KEY, RENREN_SECRET_KEY);
		rennClient.setScope("publish_share publish_feed");
		rennClient.setTokenType("mac");
		rennClient.setLoginListener(new LoginListener() {
			@Override
			public void onLoginSuccess() {
				PutShareUrlParam param = new PutShareUrlParam();
				param.setComment(comment);
				param.setUrl(url);
				try {
					rennClient.getRennService().sendAsynRequest(param,
							new CallBack() {
								@Override
								public void onSuccess(RennResponse response) {
									Toast.makeText(getActivity(), "分享成功",
											Toast.LENGTH_SHORT).show();
								}

								@Override
								public void onFailed(String errorCode,
										String errorMessage) {
									Toast.makeText(getActivity(), "分享失败",
											Toast.LENGTH_SHORT).show();
								}

							});
				} catch (RennException e) {
					Toast.makeText(getActivity(), "分享失败", Toast.LENGTH_SHORT)
							.show();
					e.printStackTrace();
				}
			}

			@Override
			public void onLoginCanceled() {
				Toast.makeText(getActivity(), getString(R.string.login_failed),
						Toast.LENGTH_SHORT).show();
			}

		});

		rennClient.login(getActivity());
	}

	private class GetDataTask extends
			AsyncTask<Void, Void, ArrayList<SimpleCourseInfo>> {

		private Runnable runOnPostExecute;

		public GetDataTask(Runnable runOnPostExecute) {
			this.runOnPostExecute = runOnPostExecute;
		}

		@Override
		protected ArrayList<SimpleCourseInfo> doInBackground(Void... params) {
			mSwipeRefreshLayout.setRefreshing(true);

			ArrayList<SimpleCourseInfo> courses = new ArrayList<SimpleCourseInfo>();

			if (!new NetworkConnectivityManager(getActivity())
					.isConnectingToInternet(false)) {
				courses = loadCourses(courseStatus);
				return courses;
			}

			try {
				UserInfo user = UserInfo.load(getActivity());

				switch (courseStatus) {
				case PAST:
					courses = Courses.past(user.getEmail(), user.getPassword());
					break;
				case CURRENT:
					courses = Courses.current(user.getEmail(),
							user.getPassword());
					break;
				case UPCOMING:
					courses = Courses.upcoming(user.getEmail(),
							user.getPassword());
					break;
				}
				new SignInStatusManager(getActivity())
						.checkSignInStatus(courses);
			} catch (IOException e) {
				e.printStackTrace();
			}

			return courses;
		}

		@Override
		protected void onPostExecute(ArrayList<SimpleCourseInfo> result) {

			Collections.sort(result, new Comparator<SimpleCourseInfo>() {

				@Override
				public int compare(SimpleCourseInfo arg0, SimpleCourseInfo arg1) {
					return arg0.getStartDate().compareTo(arg1.getStartDate());
				}

			});

			mListItems.clear();
			mListItems.addAll(result);
			saveCourses(result, courseStatus);

			if (null != runOnPostExecute) {
				runOnPostExecute.run();
			}

			super.onPostExecute(result);
			mSwipeRefreshLayout.setRefreshing(false);
		}

	}

	private void saveCourses(ArrayList<SimpleCourseInfo> courses,
			SimpleCourseStatus status) {
		String filename = getStoragePath(status);
		if (null != filename) {
			JSONArray coursesJSON = new JSONArray();
			for (SimpleCourseInfo course : courses) {
				coursesJSON.put(course.toJSON());
			}

			try {
				FileOutputStream fos = getActivity().openFileOutput(filename,
						Context.MODE_PRIVATE);
				fos.write(coursesJSON.toString().getBytes());
				fos.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private ArrayList<SimpleCourseInfo> loadCourses(SimpleCourseStatus status) {
		ArrayList<SimpleCourseInfo> courses = new ArrayList<SimpleCourseInfo>();

		String filename = getStoragePath(status);
		if (null != filename) {
			try {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(getActivity().openFileInput(
								filename)));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line);
				}
				JSONArray coursesJSON = new JSONArray(sb.toString());
				for (int i = 0; i < coursesJSON.length(); i++) {
					courses.add(SimpleCourseInfo.fromJSON(
							coursesJSON.getJSONObject(i), status));
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		return courses;
	}

	private String getStoragePath(SimpleCourseStatus status) {
		String filename = null;
		switch (status) {
		case PAST:
			filename = CACHE_COURSES_PAST;
			break;
		case CURRENT:
			filename = CACHE_COURSES_CURRENT;
			break;
		case UPCOMING:
			filename = CACHE_COURSES_UPCOMING;
			break;
		}
		return filename;
	}

}
