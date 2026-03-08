/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.otacoo.chan.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;

import org.otacoo.chan.controller.Controller;
import org.otacoo.chan.ui.view.AuthWebView;

public class WebViewController extends Controller {
    private AuthWebView webView;
    private final String url;
    private final String title;

    public WebViewController(Context context, String url, String title) {
        super(context);
        this.url = url;
        this.title = title;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.title = title;

        AuthWebView.runOnWebViewThread(this::setupWebView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        if (!alive) return;

        webView = new AuthWebView(context);
        webView.loadUrl(url);

        view = webView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }

    @Override
    public boolean onBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onBack();
    }
}
