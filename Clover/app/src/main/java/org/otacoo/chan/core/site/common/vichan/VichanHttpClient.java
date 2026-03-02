/*
 * Clover - 4chan browser https://github.com/otacoo/Clover/
 * Copyright (C) 2014  Floens https://github.com/Floens/Clover/
 * Copyright (C) 2026 otacoo
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
package org.otacoo.chan.core.site.common.vichan;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * A simple CookieJar implementation using Java's CookieManager for Vichan.
 * This bridges between OkHttp's CookieJar interface and Java's cookie management.
 */
class VichanCookieJar implements CookieJar {
    private final CookieManager cookieManager;

    public VichanCookieJar() {
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
    }

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        try {
            // Convert OkHttp cookies to HttpCookies and store them
            for (Cookie cookie : cookies) {
                java.net.HttpCookie httpCookie = new java.net.HttpCookie(cookie.name(), cookie.value());
                httpCookie.setDomain(cookie.domain());
                httpCookie.setPath(cookie.path());
                httpCookie.setSecure(cookie.secure());
                httpCookie.setHttpOnly(cookie.httpOnly());
                if (cookie.expiresAt() > System.currentTimeMillis()) {
                    httpCookie.setMaxAge((cookie.expiresAt() - System.currentTimeMillis()) / 1000);
                }
                cookieManager.getCookieStore().add(url.uri(), httpCookie);
            }
        } catch (Exception e) {
            // Ignore cookie storage errors
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> result = new ArrayList<>();
        try {
            List<java.net.HttpCookie> httpCookies = cookieManager.getCookieStore().get(url.uri());
            for (java.net.HttpCookie httpCookie : httpCookies) {
                Cookie.Builder builder = new Cookie.Builder()
                        .name(httpCookie.getName())
                        .value(httpCookie.getValue());
                
                if (httpCookie.getDomain() != null) {
                    builder.domain(httpCookie.getDomain());
                }
                if (httpCookie.getPath() != null) {
                    builder.path(httpCookie.getPath());
                }
                if (httpCookie.getSecure()) {
                    builder.secure();
                }
                if (httpCookie.isHttpOnly()) {
                    builder.httpOnly();
                }
                if (httpCookie.getMaxAge() > 0) {
                    builder.expiresAt(System.currentTimeMillis() + httpCookie.getMaxAge() * 1000);
                }
                result.add(builder.build());
            }
        } catch (Exception e) {
            // Ignore cookie loading errors
        }
        return result;
    }
}

