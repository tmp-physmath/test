package storage.cloudStorage;

import static system.ErrorConst.CANNOT_AUTH;
import static system.ErrorConst.DBXEXCEPTION;
import static system.ErrorConst.EXCEPTION;
import static system.ErrorConst.FAILED_PROCESS;
import static system.ErrorConst.IOEXCEPTION;
import static system.ErrorConst.SUCCESS_PROCESS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import storage.IStorage;
import storage.virtualStorage.VirtualFile;
import system.IAuthorization;

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxEntry.WithChildren;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxRequestUtil;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.http.HttpRequestor.Response;

public class DropBoxStorage implements IStorage {
	
	final static String APP_KEY = "y41d8ridamc9uqv";
	final static String APP_SECRET = "kx3i1y2vagtqzvj";
	
	static String accessToken = "83aJRCgbahQAAAAAAAALtSkm-YNn13rYd2_UCxUz0etYZev6TuLYdFGII6tENQo_";
	
	String userId;
	
	public DropBoxStorage(String userId) {
		this.userId = userId;
	}
	
	@Override
	public int add(File inputFile, String name) {
		//認証しているか確認
		if (!assertAuth()) {
			return CANNOT_AUTH;
		}
		
		try (FileInputStream inputStream = new FileInputStream(inputFile);) {
			//アップロード実行(すでにデータが存在した場合は上書きする)
			getClient().uploadFile("/" + name, DbxWriteMode.force(), inputFile.length(), inputStream);
		} catch (IOException e) {
			return IOEXCEPTION;
		} catch (Exception e) {
			return EXCEPTION;
		}
		return SUCCESS_PROCESS;
	}

	/**
	 * 認証されているか確認して、認証していなければ認証を行う
	 * @return
	 */
	private boolean assertAuth() {
		//リフレッシュ認証できないので認証が切れているときは常に失敗にする
		return isAuthed();
	}

	@Override
	public int delete(VirtualFile file) {
		//認証しているか確認
		if (!assertAuth()) {
			return CANNOT_AUTH;
		}
		
		try {
			getClient().delete("/" + file.path_);
		} catch (DbxException e) {
			return DBXEXCEPTION;
		} catch (Exception e) {
			return EXCEPTION;
		}
		return SUCCESS_PROCESS;
	}

	@Override
	public int rename(VirtualFile target, String name) {
		//認証しているか確認
		if (!assertAuth()) {
			return CANNOT_AUTH;
		}
		DbxEntry dbxEntry = null;
		try {
			//移動する
			dbxEntry = getClient().move("/" + target.path_, "/" + name);
		} catch (DbxException e) {
			return DBXEXCEPTION;
		} catch (Exception e) {
			return EXCEPTION;
		}
		return dbxEntry != null ? SUCCESS_PROCESS : FAILED_PROCESS;
	}

	@Override
	public int download(VirtualFile target, File distinct) {
		//認証しているか確認
		if (!assertAuth()) {
			return CANNOT_AUTH;
		}
		
		boolean isSuccess;
		try (FileOutputStream outputStream = new FileOutputStream(distinct)) {
			isSuccess = getClient().getFile("/" + target.path_, null, outputStream) != null;
		} catch (IOException e) {
			return IOEXCEPTION;
		} catch (Exception e) {
			return EXCEPTION;
		}
		return isSuccess ? SUCCESS_PROCESS : FAILED_PROCESS;
	}

	@Override
	public long getFreeSpace() {
		return 0;
	}

	@Override
	public int refreshAuth() {
		return FAILED_PROCESS;
	}

	@Override
	public boolean isAuthed() {
		try {
			//アクセストークンがnullの時は強制的に認証されていないとする
			if (accessToken == null) {
				return false;
			}
			
			//一旦利用者情報を取得してステータスコードが200でないなら認証されていないとする
			DbxClient client = getClient();
			Handle handle = new Handle();
			DbxAccountInfo accountInfo = client.doPost(DbxHost.Default.api, "1/account/info", null, null, handle);
			
			//accessTokenが正しいか確認
			if (accountInfo == null || !handle.isAuth()) {
				return false;
			}
			
			//ユーザのアカウントが正しいか確認
			if (!accountInfo.displayName.equals(userId)) {
				return false;
			}
			return true;
		} catch (DbxException e) {
			return false;
		}
	}
	
	class Handle extends DbxRequestUtil.ResponseHandler<DbxAccountInfo> {
		boolean isAuth;
		
		@Override
		public DbxAccountInfo handle(Response response) throws DbxException {
			isAuth = (response.statusCode == 200);
			
			//正しく認証が通っていればアカウント情報を返す
			if (isAuth) {
				return DbxRequestUtil.readJsonFromResponse(DbxAccountInfo.Reader, response.body);
			//認証が通ってなければnullを返す
			} else {
				return null;
			}
		}
		
		public boolean isAuth() {
			return isAuth;
		}
	}
	
	/**
	 * DropBoxクライアントを取得
	 * @return
	 */
	protected DbxClient getClient() {
		return new DbxClient(getConfig(), accessToken);
	}
	
	//リクエストコンフィグ
	protected static DbxRequestConfig requestConfig = null;
	
	/**
	 * リクエストコンフィグを取得
	 * @return
	 */
	protected static DbxRequestConfig getConfig() {
		if (requestConfig == null) {
			requestConfig = new DbxRequestConfig("TrendUser", Locale.getDefault().toString());
		}
		return requestConfig;
	}

	@Override
	public List<VirtualFile> fileList() {
		//認証しているか確認
		if (!assertAuth()) {
			return new ArrayList<>();
		}
		//ファイルのリスト
		ArrayList<VirtualFile> fileList = new ArrayList<>();
		try {
			//rootフォルダにある全てのファイルを取得する
			WithChildren filesMetaList = getClient().getMetadataWithChildren("/");
			for (DbxEntry file : filesMetaList.children) {
				//ファイルの時だけ追加する
				if (file.isFile()) {
					fileList.add(new VirtualFile(file.name));
				}
			}
		} catch (DbxException e) {
			return fileList;
		}
		return fileList;
	}

	@Override
	public boolean exist(VirtualFile file) {
		//認証しているか確認
		if (!assertAuth()) {
			return false;
		}
		DbxEntry metadata;
		try {
			metadata = getClient().getMetadata("/" + file.path_);
		} catch (Exception e) {
			return false;
		}
		return metadata != null;
	}
	
//	//TODO 必要なければ、場所を移すかあとで消します
//	/**
//	 * web認証を行うための一時的なメソッド
//	 * @throws DbxException 
//	 */
//	public void webAuth() throws DbxException {
//		DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
//
//		DbxRequestConfig config = getConfig();
//		DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);
//		
//		//URL発行
//		String authorizeUrl = webAuth.start();
//		System.out.println("1. Go to: " + authorizeUrl);
//		System.out.println("2. Click \"Allow\" (you might have to log in first)");
//		System.out.println("3. Copy the authorization code.");
//		
//		//webのコードを入力させる
//		String code = null;
//		try (Scanner sc = new Scanner(new BufferedReader(new InputStreamReader(System.in)))) {
//			code = sc.next();
//		}
//		
//		//コードチェック
//		if (code == null) {
//			System.err.println("入力エラー");
//			return;
//		}
//		
//		DbxAuthFinish authFinish = webAuth.finish(code);
//		String accessToken = authFinish.accessToken;
//		
//		System.out.println("アクセストークン:" + accessToken);
//		
//		this.accessToken = accessToken;
//	}

	@Override
	public List<IAuthorization> getAuthorization() {
		//認証されていれば空のリストを返す
		if (isAuthed()) {
			return new ArrayList<>();
		}
		
		ArrayList<IAuthorization> authList = new ArrayList<IAuthorization>();
		authList.add(new DropBoxAuthorizationImpl());
		return authList;
	}
	
	class DropBoxAuthorizationImpl implements IAuthorization{
		@Override
		public String authURL() {
			try {
				DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
	
				DbxRequestConfig config = getConfig();
				DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);
				//URL発行
				return  webAuth.start();
			} catch (Exception e) {
				return null;
			}
		}

		@Override
		public boolean auth(String key) {
			if (key == null) {
				return false;
			}
			
			try {
				DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
				DbxAuthFinish authFinish;
				authFinish = new DbxWebAuthNoRedirect(getConfig(), appInfo).finish(key);
				accessToken = authFinish.accessToken;
			} catch (DbxException e) {
				return false;
			} catch (Exception e) {
				return false;
			}
			return true;
		}
		
	}
}
