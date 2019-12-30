/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.router_app.Helper;

import android.app.Activity;
import android.view.View;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

/**
 * Helper to manage the sample snackbar. Hides the Android boilerplate code, and exposes simpler
 * methods.
 */
public final class SnackbarHelper {
  private static final int BACKGROUND_COLOR = 0xbf323232;
  private Snackbar messageSnackbar;
  private enum DismissBehavior { HIDE, SHOW, FINISH };
  private int maxLines = 2;
  private String lastMessage = "";

  /** Shows a snackbar with a given message. */
  public void showMessage(Activity activity, String message) {
    if (!message.isEmpty() && (messageSnackbar == null || !lastMessage.equals(message))) {
      lastMessage = message;
      show(activity, message, DismissBehavior.HIDE);
    }
  }

    private void show(
            final Activity activity, final String message, final DismissBehavior dismissBehavior) {
        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        messageSnackbar =
                                Snackbar.make(
                                        activity.findViewById(android.R.id.content),
                                        message,
                                        Snackbar.LENGTH_INDEFINITE);
                        messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
                        if (dismissBehavior != DismissBehavior.HIDE) {
                            messageSnackbar.setAction(
                                    "Dismiss",
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            messageSnackbar.dismiss();
                                        }
                                    });
                            if (dismissBehavior == DismissBehavior.FINISH) {
                                messageSnackbar.addCallback(
                                        new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                                            @Override
                                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                                super.onDismissed(transientBottomBar, event);
                                                activity.finish();
                                            }
                                        });
                            }
                        }
                        messageSnackbar.show();
                    }
                });
    }

    public void hide(Activity activity) {
        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (messageSnackbar != null) {
                            messageSnackbar.dismiss();
                        }
                        messageSnackbar = null;
                    }
                });
    }
}
