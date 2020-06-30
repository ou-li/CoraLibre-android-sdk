/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.coralibre.android.sdk;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.coralibre.android.sdk.backend.ResponseCallback;
import org.coralibre.android.sdk.internal.AppConfigManager;
import org.coralibre.android.sdk.internal.BroadcastHelper;
import org.coralibre.android.sdk.internal.ErrorHelper;
import org.coralibre.android.sdk.internal.TracingService;
import org.coralibre.android.sdk.internal.crypto.CryptoModule;
import org.coralibre.android.sdk.internal.database.Database;
import org.coralibre.android.sdk.internal.database.models.ExposureDay;
import org.coralibre.android.sdk.internal.logger.Logger;
import org.coralibre.android.sdk.internal.util.ProcessUtil;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import okhttp3.CertificatePinner;

public class DP3T {

	private static final String TAG = "DP3T Interface";

	public static final String UPDATE_INTENT_ACTION = "org.coralibre.android.sdk.UPDATE_ACTION";

	private static boolean isInitialized = false;

	public static void init(Context context) {
		// TODO: there's no else branch, that's bad.
		if (ProcessUtil.isMainProcess(context)) {
			executeInit(context);
			DP3T.isInitialized = true;
		}
	}

	@Deprecated
	public static void init(Context context, PublicKey signaturePublicKey) {
		init(context);
	}

	@Deprecated
	public static void init(Context context, String appId, PublicKey signaturePublicKey) {
		init(context);
	}

	@Deprecated
	public static void init(Context context, String appId, boolean enableDevDiscoveryMode, PublicKey signaturePublicKey) {
		init(context);
	}

	private static void executeInit(Context context) {
		CryptoModule.getInstance(context).init();

		new Database(context).removeOldData();

		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		boolean advertising = appConfigManager.isAdvertisingEnabled();
		boolean receiving = appConfigManager.isReceivingEnabled();
		if (advertising || receiving) {
			start(context, advertising, receiving);
		}
	}

	private static void checkInit() throws IllegalStateException {
		if (!DP3T.isInitialized) {
			throw new IllegalStateException("You have to call DP3T.init() in your application onCreate()");
		}
	}

	public static void start(Context context) {
		start(context, true, true);
	}

	protected static void start(Context context, boolean advertise, boolean receive) {
		checkInit();
		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		appConfigManager.setAdvertisingEnabled(advertise);
		appConfigManager.setReceivingEnabled(receive);
		long scanInterval = appConfigManager.getScanInterval();
		long scanDuration = appConfigManager.getScanDuration();
		Intent intent = new Intent(context, TracingService.class).setAction(TracingService.ACTION_START);
		intent.putExtra(TracingService.EXTRA_ADVERTISE, advertise);
		intent.putExtra(TracingService.EXTRA_RECEIVE, receive);
		intent.putExtra(TracingService.EXTRA_SCAN_INTERVAL, scanInterval);
		intent.putExtra(TracingService.EXTRA_SCAN_DURATION, scanDuration);
		ContextCompat.startForegroundService(context, intent);
		BroadcastHelper.sendUpdateBroadcast(context);
	}

	public static boolean isStarted(Context context) {
		checkInit();
		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		return appConfigManager.isAdvertisingEnabled() || appConfigManager.isReceivingEnabled();
	}

	@Deprecated
	public static void sync(Context context) {
		checkInit();
		// kept for temporary compatibility
	}

	public static TracingStatus getStatus(Context context) {
		checkInit();
		Database database = new Database(context);
		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		Collection<TracingStatus.ErrorState> errorStates = ErrorHelper.checkTracingErrorStatus(context);
		List<ExposureDay> exposureDays = database.getExposureDays();
		InfectionStatus infectionStatus;
		if (appConfigManager.getIAmInfected()) {
			infectionStatus = InfectionStatus.INFECTED;
		} else if (exposureDays.size() > 0) {
			infectionStatus = InfectionStatus.EXPOSED;
		} else {
			infectionStatus = InfectionStatus.HEALTHY;
		}
		return new TracingStatus(
				database.getContacts().size(),
				appConfigManager.isAdvertisingEnabled(),
				appConfigManager.isReceivingEnabled(),
				appConfigManager.getLastSyncDate(),
				infectionStatus,
				exposureDays,
				errorStates
		);
	}

	@Deprecated
	public static void sendIAmInfected(Context context, Date onset, Object exposeeAuthMethod,
			ResponseCallback<Void> callback) {
		checkInit();
		// Kept for temporary compatibility
	}

	@Deprecated
	public static void sendFakeInfectedRequest(Context context, Date onset, Object exposeeAuthMethod)
			throws NoSuchAlgorithmException, IOException {
		checkInit();
		// Kept for temporary compatibility
	}

	public static void stop(Context context) {
		checkInit();

		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		appConfigManager.setAdvertisingEnabled(false);
		appConfigManager.setReceivingEnabled(false);

		Intent intent = new Intent(context, TracingService.class).setAction(TracingService.ACTION_STOP);
		context.startService(intent);
		BroadcastHelper.sendUpdateBroadcast(context);
	}

	public static void setMatchingParameters(Context context, float contactAttenuationThreshold, int numberOfWindowsForExposure) {
		checkInit();

		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		appConfigManager.setContactAttenuationThreshold(contactAttenuationThreshold);
		appConfigManager.setNumberOfWindowsForExposure(numberOfWindowsForExposure);
	}

	@Deprecated
	public static void setCertificatePinner(@NonNull CertificatePinner certificatePinner) {
		// Kept for temporary compatibility
	}

	public static IntentFilter getUpdateIntentFilter() {
		return new IntentFilter(DP3T.UPDATE_INTENT_ACTION);
	}

	public static void clearData(Context context, Runnable onDeleteListener) {
		checkInit();
		AppConfigManager appConfigManager = AppConfigManager.getInstance(context);
		if (appConfigManager.isAdvertisingEnabled() || appConfigManager.isReceivingEnabled()) {
			throw new IllegalStateException("Tracking must be stopped for clearing the local data");
		}

		CryptoModule.getInstance(context).reset();
		appConfigManager.clearPreferences();
		Logger.clear();
		Database db = new Database(context);
		db.recreateTables(response -> onDeleteListener.run());
	}

}
