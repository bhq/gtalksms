package com.googlecode.gtalksms;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import org.jivesoftware.smack.util.StringUtils;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.googlecode.gtalksms.tools.Tools;
/**
 * 
 * @author GTalkSMS Team
 * 
 * In order to work flawlessly with the BackupAgent
 * ALL settings in SettingsManager have to be of the same type
 * as within the SharedPreferences back-end AND they need to have
 * the same name
 *
 */
public class SettingsManager {
    public static final String[] xmppConnectionSettings = { "serverHost", "serviceName", "serverPort", 
                                                            "login", "password", "useDifferentAccount",
                                                            "xmppSecurityMode", "manuallySpecifyServerSettings",
                                                            "useCompression"};
    
    public static final int XMPPSecurityDisabled = 1;
    public static final int XMPPSecurityRequired = 2;
    public static final int XMPPSecurityOptional = 3;
    
    public static final String FRAMEBUFFER_ARGB_8888 = "ARGB_8888";
    public static final String FRAMEBUFFER_RGB_565 = "RGB_565";
    
    // XMPP connection
    public String serverHost;
    public String serviceName;
    public int serverPort;
    
    private String _login;
    public String getLogin() { return _login; }
    public void setLogin(String value) { _login = saveSetting("login", value); }
    
    private String _password;
    public String getPassword() { return _password; }
    public void setPassword(String value) { _password = saveSetting("password", value); }

    private String _notifiedAddress;
    private ArrayList<String> _notifiedAddresses = new ArrayList<String>();
    public String[] getNotifiedAddresses() { return _notifiedAddresses.toArray(new String[_notifiedAddresses.size()]); }
    public void setNotifiedAddress(String value) { 
        _notifiedAddress = saveSetting("notifiedAddress", value);
        updateNotifiedAddresses();
    }
    
    public void updateNotifiedAddresses() { 
        _notifiedAddresses.clear();
        for (String str : TextUtils.split(_notifiedAddress, "\\|")) {
            _notifiedAddresses.add(str.toLowerCase());
        }
    }

    public boolean containsNotifiedAddress(String value) { 
        return _notifiedAddresses.contains(value.toLowerCase());
    }

    /**
     * Checks if the given fromJid is part of the notified Address set. fromJid can either be a fullJid or a bareJid
     * 
     * @param fromJid
     *            The JID we received a message from
     * @return true if the given JID is part of the notified Address set, otherwise false
     */
    public boolean cameFromNotifiedAddress(String fromJid) {
        String sanitizedNotifiedAddress = null;
        String sanitizedJid = fromJid.toLowerCase();
        for (String notifiedAddress : _notifiedAddresses) {
            sanitizedNotifiedAddress = notifiedAddress.toLowerCase();
            // If it's a fullJID, append a slash for security reasons
            if (sanitizedJid.startsWith(sanitizedNotifiedAddress + "/")
            // A bare JID should be equals to one of the notified Address set
                    || sanitizedNotifiedAddress.equals(sanitizedJid)) {
                return true;
            }
        }
        return false;
    }
    
    public void addNotifiedAddress(String value) { 
        if (! containsNotifiedAddress(value)) {
            _notifiedAddresses.add(value);
        }
        setNotifiedAddress(TextUtils.join("|", _notifiedAddresses));
    }

    public void removeNotifiedAddress(String value) { 
        if (containsNotifiedAddress(value)) {
            _notifiedAddresses.remove(value);
        }
        setNotifiedAddress(TextUtils.join("|", _notifiedAddresses));
    }
    
    private boolean _connectOnMainScreenStartup;
    
    public boolean getConnectOnMainScreenStartup() { 
        return _connectOnMainScreenStartup; 
    }

    public void setConnectOnMainScreenStartup(boolean value) { 
        _connectOnMainScreenStartup = saveSetting("connectOnMainscreenShow", value); 
    }
    
    public String roomPassword;
    public String mucServer;
    public boolean forceMucServer;
    public boolean useCompression;
    public String xmppSecurityMode;
    public int xmppSecurityModeInt;
    public boolean manuallySpecifyServerSettings;

    public static boolean connectionSettingsObsolete;
    
    // notifications
    public boolean notifyApplicationConnection;
    public boolean formatResponses;
    public boolean showStatusIcon;
    public boolean displayContactNumber;
    
    // geo location
    public boolean useGoogleMapUrl;
    public boolean useOpenStreetMapUrl;

    // ring
    public String ringtone = null;

    // battery
    public boolean notifyBatteryInStatus;
    public boolean notifyBattery;
    public int batteryNotificationIntervalInt;
    public String batteryNotificationInterval;

    // sms
    public int smsNumber;
    public boolean notifySmsSent;
    public boolean notifySmsDelivered;
    public boolean notifySmsSentDelivered;
    public boolean notifyIncomingCalls;
    public boolean notifySmsInChatRooms;
    public boolean notifySmsInSameConversation;
    public boolean notifyInMuc;
    public boolean smsReplySeparate;
    public boolean markSmsReadOnReply;
    public String smsMagicWord;
    
    // screen shots
    public String framebufferMode;
    
    // calls
    public int callLogsNumber;
    
    // locale
    public Locale locale;
    
    // app settings
    public boolean debugLog;
    public String displayIconIndex;
    
    // auto start and stop settings
    public boolean startOnBoot;
    public boolean startOnPowerConnected;
    public boolean startOnWifiConnected;
    public boolean stopOnPowerDisconnected;
    public boolean stopOnWifiDisconnected;
    public int stopOnPowerDelay;
    
    // public intents settings
    public boolean publicIntentsEnabled;
    public boolean publicIntentTokenRequired;
    public String publicIntentToken;
    
    // recipient command settings
    public boolean dontDisplayRecipient;
    
    private static SettingsManager sSettingsManager = null;
    
    private SharedPreferences mSharedPreferences;
    private Context mContext;
    private OnSharedPreferenceChangeListener mChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (debugLog) {
                Log.i(Tools.LOG_TAG, "Preferences updated: key=" + key);
            }
            try {
                importPreferences();
            } catch (Exception e) {
                Log.e(Tools.LOG_TAG, "Failed to load settings", e);
            }
            OnPreferencesUpdated(key);
        }
    };
    
    private SettingsManager(Context context) {
        mContext = context;
        mSharedPreferences = mContext.getSharedPreferences(Tools.APP_NAME, 0);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mChangeListener);
        
        try {
            importPreferences();
        } catch (Exception e) {
            Log.e(Tools.LOG_TAG, "Failed to load settings", e);
        }
    }
    
    public static SettingsManager getSettingsManager(Context context) {
        if (sSettingsManager == null) {
            sSettingsManager = new SettingsManager(context);           
        } 
        return sSettingsManager;        
    }
    
    public void Destroy() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mChangeListener);
    }
    
    public SharedPreferences.Editor getEditor() {
        return mSharedPreferences.edit();
    }
    
    public Boolean saveSetting(String key, Boolean value) {
        getEditor().putBoolean(key, (Boolean)value).commit();
        return value;
    }
    
    public String saveSetting(String key, String value) {
        getEditor().putString(key, (String)value).commit();
        return value;
    }
    
    public Integer saveSetting(String key, Integer value) {
        getEditor().putInt(key, (Integer)value).commit();
        return value;
    }
    
    public Map<String, ?> getAllSharedPreferences() {
        return mSharedPreferences.getAll();
    }
    
    public boolean SharedPreferencesContains(String key) {
        return mSharedPreferences.contains(key);
    }

    public void OnPreferencesUpdated(String key) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
            BackupManager bm = new BackupManager(mContext);
            bm.dataChanged();
        }
        for (String s : xmppConnectionSettings) {
            if (s.equals(key)) {
                connectionSettingsObsolete = true;
            }
        }
        if (key.equals("locale")) {
            Tools.setLocale(this, mContext);
        }
    }
    
    /** imports the preferences */
    private void importPreferences() {
        serverHost = mSharedPreferences.getString("serverHost", "");
        serverPort = mSharedPreferences.getInt("serverPort", 0);
        
        _notifiedAddress = mSharedPreferences.getString("notifiedAddress", "");
        updateNotifiedAddresses();
        _login = mSharedPreferences.getString("login", "");
        
        manuallySpecifyServerSettings = mSharedPreferences.getBoolean("manuallySpecifyServerSettings", false);
        if (manuallySpecifyServerSettings) {
            serviceName = mSharedPreferences.getString("serviceName", "");
        } else {
            serviceName = StringUtils.parseServer(_login);
        }
        
        _password =  mSharedPreferences.getString("password", "");
        xmppSecurityMode = mSharedPreferences.getString("xmppSecurityMode", "opt");
        if(xmppSecurityMode.equals("req")) {
            xmppSecurityModeInt = XMPPSecurityRequired;
        } else if (xmppSecurityMode.equals("dis")) {
            xmppSecurityModeInt = XMPPSecurityDisabled;
        } else {
            xmppSecurityModeInt = XMPPSecurityOptional;
        }
        useCompression = mSharedPreferences.getBoolean("useCompression", false);
        
        useGoogleMapUrl = mSharedPreferences.getBoolean("useGoogleMapUrl", true);
        useOpenStreetMapUrl = mSharedPreferences.getBoolean("useOpenStreetMapUrl", false);
        
        showStatusIcon = mSharedPreferences.getBoolean("showStatusIcon", false);
        
        notifyApplicationConnection = mSharedPreferences.getBoolean("notifyApplicationConnection", false);
        notifyBattery = mSharedPreferences.getBoolean("notifyBattery", false);
        notifyBatteryInStatus = mSharedPreferences.getBoolean("notifyBatteryInStatus", true);
        batteryNotificationInterval = mSharedPreferences.getString("batteryNotificationInterval", "10");
        batteryNotificationIntervalInt = Integer.parseInt(batteryNotificationInterval);
        notifySmsSent = mSharedPreferences.getBoolean("notifySmsSent", true);
        notifySmsDelivered = mSharedPreferences.getBoolean("notifySmsDelivered", false);
        notifySmsSentDelivered = notifySmsSent || notifySmsDelivered;
        ringtone = mSharedPreferences.getString("ringtone", Settings.System.DEFAULT_RINGTONE_URI.toString());
        markSmsReadOnReply = mSharedPreferences.getBoolean("markSmsReadOnReply", false);
        smsNumber = mSharedPreferences.getInt("smsNumber", 5);
        callLogsNumber = mSharedPreferences.getInt("callLogsNumber", 10);
        formatResponses = mSharedPreferences.getBoolean("formatResponses", false);
        displayContactNumber = mSharedPreferences.getBoolean("displayContactNumber", false);
        notifyIncomingCalls = mSharedPreferences.getBoolean("notifyIncomingCalls", false);
        displayIconIndex = mSharedPreferences.getString("displayIconIndex", "0");
        
        String localeStr = mSharedPreferences.getString("locale", "default");
        if (localeStr.equals("default")) {
            locale = Locale.getDefault();
        } else {
            locale = new Locale(localeStr);
        }
        
        roomPassword = mSharedPreferences.getString("roomPassword", "gtalksms");
        forceMucServer = mSharedPreferences.getBoolean("forceMucServer", false);
        mucServer = mSharedPreferences.getString("mucServer", "conference.jwchat.org");
        String notificationIncomingSmsType = mSharedPreferences.getString("notificationIncomingSmsType", "same");
        
        if (notificationIncomingSmsType.equals("both")) {
            notifySmsInChatRooms = true;
            notifySmsInSameConversation = true;
        } else if (notificationIncomingSmsType.equals("no")) {
            notifySmsInChatRooms = false;
            notifySmsInSameConversation = false;
        } else if (notificationIncomingSmsType.equals("separate")) {
            notifySmsInChatRooms = true;
            notifySmsInSameConversation = false;
        } else {
            notifySmsInSameConversation = true;
            notifySmsInChatRooms = false;
        }
        smsMagicWord = mSharedPreferences.getString("smsMagicWord", "GTalkSMS");
        notifyInMuc = mSharedPreferences.getBoolean("notifyInMuc", false); 
        smsReplySeparate = mSharedPreferences.getBoolean("smsReplySeparate", false);
        framebufferMode = mSharedPreferences.getString("framebufferMode", "ARGB_8888");
        _connectOnMainScreenStartup = mSharedPreferences.getBoolean("connectOnMainscreenShow", false);
        debugLog = mSharedPreferences.getBoolean("debugLog", false);
        
        // auto start and stop settings
        startOnBoot = mSharedPreferences.getBoolean("startOnBoot", false);
        startOnPowerConnected = mSharedPreferences.getBoolean("startOnPowerConnected", false);
        startOnWifiConnected = mSharedPreferences.getBoolean("startOnWifiConnected", false);
        stopOnPowerDisconnected = mSharedPreferences.getBoolean("stopOnPowerDisconnected", false);
        stopOnWifiDisconnected = mSharedPreferences.getBoolean("stopOnWifiDisconnected", false);
        stopOnPowerDelay = mSharedPreferences.getInt("stopOnPowerDelay", 1);
        
        // pulic intent settings
        publicIntentsEnabled = mSharedPreferences.getBoolean("publicIntentsEnabled", false);
        publicIntentTokenRequired = mSharedPreferences.getBoolean("publicIntentTokenRequired", false);
        publicIntentToken = mSharedPreferences.getString("publicIntentToken", "secret");
        
        // reply command settings
        dontDisplayRecipient = false;
    }
}
