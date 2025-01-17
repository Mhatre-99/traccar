/*
 * Copyright 2015 - 2022 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import org.traccar.api.BaseResource;
import org.traccar.api.security.CodeRequiredException;
import org.traccar.api.security.LoginService;
import org.traccar.api.signature.TokenManager;
import org.traccar.database.OpenIdProvider;
import org.traccar.helper.DataConverter;
import org.traccar.helper.LogAction;
import org.traccar.helper.WebHelper;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import com.nimbusds.oauth2.sdk.ParseException;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.net.URI;

@Path("session")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
public class SessionResource extends BaseResource {


    public static final String USER_ID_KEY = "userId";
    public static final String USER_COOKIE_KEY = "user";
    public static final String PASS_COOKIE_KEY = "password";

    @Inject
    private LoginService loginService;

    @Inject
    @Nullable
    private OpenIdProvider openIdProvider;

    @Inject
    private TokenManager tokenManager;

    @Context
    private HttpServletRequest request;

    @PermitAll
    @GET
    public User getUserGivenToken(@QueryParam("token") String token)
            throws StorageException, IOException, GeneralSecurityException {
        if (token != null) {
            return getUserTokenAuthentication(token);
        }
        Long userId = (Long) request.getSession().getAttribute(USER_ID_KEY);
        if (userId == null) {
            return getUserTokenCookieAuthentication();
        } else {
            User user = permissionsService.getUser(userId);
            if (user != null) {
                return user;
            }
        }
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND).build());

    }

    private User getUserTokenAuthentication(String token)
            throws StorageException, IOException, GeneralSecurityException {
        User user = loginService.login(token);
        if (user != null) {
            request.getSession().setAttribute(USER_ID_KEY, user.getId());
            LogAction.login(user.getId(), WebHelper.retrieveRemoteAddress(request));
            return user;
        }
        return null;
    }

    private User getUserTokenCookieAuthentication()
            throws StorageException {
        Cookie[] cookies = request.getCookies();
        String email = null, password = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(USER_COOKIE_KEY)) {
                    byte[] emailBytes = DataConverter.parseBase64(
                            URLDecoder.decode(cookie.getValue(), StandardCharsets.US_ASCII));
                    email = new String(emailBytes, StandardCharsets.UTF_8);
                } else if (cookie.getName().equals(PASS_COOKIE_KEY)) {
                    byte[] passwordBytes = DataConverter.parseBase64(
                            URLDecoder.decode(cookie.getValue(), StandardCharsets.US_ASCII));
                    password = new String(passwordBytes, StandardCharsets.UTF_8);
                }
            }
        }
        if (email != null && password != null) {
            return getUserTokenCredentialAuthentication(email, password);
        }
        return null;
    }

    private User getUserTokenCredentialAuthentication(String email, String password)
            throws StorageException {
        User user = loginService.login(email, password, null);
        if (user != null) {
            request.getSession().setAttribute(USER_ID_KEY, user.getId());
            LogAction.login(user.getId(), WebHelper.retrieveRemoteAddress(request));
            return user;
        }
        return null;
    }



    @Path("{id}")
    @GET
    public User getUserGivenId(@PathParam("id") long userId) throws StorageException {
        permissionsService.checkUser(getUserId(), userId);
        User user = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", userId)));
        request.getSession().setAttribute(USER_ID_KEY, user.getId());
        LogAction.login(user.getId(), WebHelper.retrieveRemoteAddress(request));
        return user;
    }

    @PermitAll
    @POST
    public User add(
            @FormParam("email") String email,
            @FormParam("password") String password,
            @FormParam("code") Integer code) throws StorageException {
        User user;
        try {
            user = loginService.login(email, password, code);
        } catch (CodeRequiredException e) {
            Response response = Response
                    .status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "TOTP")
                    .build();
            throw new WebApplicationException(response);
        }
        if (user != null) {
            request.getSession().setAttribute(USER_ID_KEY, user.getId());
            LogAction.login(user.getId(), WebHelper.retrieveRemoteAddress(request));
            return user;
        } else {
            LogAction.failedLogin(WebHelper.retrieveRemoteAddress(request));
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    @DELETE
    public Response remove() {
        LogAction.logout(getUserId(), WebHelper.retrieveRemoteAddress(request));
        request.getSession().removeAttribute(USER_ID_KEY);
        return Response.noContent().build();
    }

    @Path("token")
    @POST
    public String requestToken(
            @FormParam("expiration") Date expiration) throws StorageException, GeneralSecurityException, IOException {
        return tokenManager.generateToken(getUserId(), expiration);
    }

    @PermitAll
    @Path("openid/auth")
    @GET
    public Response openIdAuth() {
        return Response.seeOther(openIdProvider.createAuthUri()).build();
    }

    @PermitAll
    @Path("openid/callback")
    @GET
    public Response requestToken() throws IOException, StorageException, ParseException, GeneralSecurityException {
        StringBuilder requestUrl = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        String requestUri = requestUrl.append('?').append(queryString).toString();

        return Response.seeOther(openIdProvider.handleCallback(URI.create(requestUri), request, USER_ID_KEY)).build();
    }
}
