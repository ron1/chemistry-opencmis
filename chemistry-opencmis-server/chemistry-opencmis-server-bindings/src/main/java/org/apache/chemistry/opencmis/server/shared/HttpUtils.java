/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.server.shared;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.chemistry.opencmis.commons.data.CacheHeaderContentStream;
import org.apache.chemistry.opencmis.commons.data.ContentLengthContentStream;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.LastModifiedContentStream;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.DateTimeHelper;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.server.impl.CallContextImpl;

/**
 * Utility methods that are used by the AtomPub and Browser binding.
 */
public class HttpUtils {

    private HttpUtils() {
    }

    /**
     * Creates a {@link CallContext} object from a servlet request.
     */
    public static CallContext createContext(HttpServletRequest request, HttpServletResponse response,
            ServletContext servletContext, String binding, CallContextHandler callContextHandler, File tempDir,
            int memoryThreshold) {
        String[] pathFragments = splitPath(request);

        String repositoryId = null;
        if (pathFragments.length > 0) {
            repositoryId = pathFragments[0];
        }

        CallContextImpl context = new CallContextImpl(binding, repositoryId, true);

        // call call context handler
        if (callContextHandler != null) {
            Map<String, String> callContextMap = callContextHandler.getCallContextMap(request);
            if (callContextMap != null) {
                for (Map.Entry<String, String> e : callContextMap.entrySet()) {
                    context.put(e.getKey(), e.getValue());
                }
            }
        }

        // servlet context and HTTP servlet request and response
        context.put(CallContext.SERVLET_CONTEXT, servletContext);
        context.put(CallContext.HTTP_SERVLET_REQUEST, request);
        context.put(CallContext.HTTP_SERVLET_RESPONSE, response);

        // temp files
        context.put(CallContext.TEMP_DIR, tempDir);
        context.put(CallContext.MEMORY_THRESHOLD, memoryThreshold);

        // decode range
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {
            rangeHeader = rangeHeader.trim();
            BigInteger offset = null;
            BigInteger length = null;

            int eq = rangeHeader.indexOf('=');
            int ds = rangeHeader.indexOf('-');
            if ((eq > 0) && (ds > eq)) {
                String offsetStr = rangeHeader.substring(eq + 1, ds).trim();
                if (offsetStr.length() > 0) {
                    offset = new BigInteger(offsetStr);
                }

                if (ds < rangeHeader.length()) {
                    String lengthStr = rangeHeader.substring(ds + 1).trim();
                    if (lengthStr.length() > 0) {
                        if (offset == null) {
                            length = new BigInteger(lengthStr);
                        } else {
                            length = (new BigInteger(lengthStr)).subtract(offset);
                        }
                    }

                    if (offset != null) {
                        context.put(CallContext.OFFSET, offset);
                    }
                    if (length != null) {
                        context.put(CallContext.LENGTH, length);
                    }
                }
            }
        }

        // get locale
        String acceptLanguage = request.getHeader("Accept-Language");
        if (acceptLanguage != null) {
            String[] locale = acceptLanguage.split("-");
            context.put(CallContext.LOCALE_ISO639_LANGUAGE, locale[0].trim());
            if (locale.length > 1) {
                int x = locale[1].indexOf(',');
                if (x == -1) {
                    context.put(CallContext.LOCALE_ISO3166_COUNTRY, locale[1].trim());
                } else {
                    context.put(CallContext.LOCALE_ISO3166_COUNTRY, locale[1].substring(0, x).trim());
                }
            }
        }

        return context;
    }

    /**
     * Splits the path into its fragments.
     */
    public static String[] splitPath(HttpServletRequest request) {
        String p = request.getPathInfo();
        if (p == null) {
            return new String[0];
        }

        return p.substring(1).split("/");
    }

    // -------------------------------------------------------------------------
    // --- HTTP headers ---
    // -------------------------------------------------------------------------

    /**
     * Sets certain HTTP headers if the server implementation requested them.
     */
    public static boolean setContentStreamHeaders(ContentStream content, HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // check if Last-Modified header should be set
        if (content instanceof LastModifiedContentStream) {
            GregorianCalendar lastModified = ((LastModifiedContentStream) content).getLastModified();
            if (lastModified != null) {
                long lastModifiedSecs = (long) Math.floor((double) lastModified.getTimeInMillis() / 1000);

                Date modifiedSince = DateTimeHelper.parseHttpDateTime(request.getHeader("If-Modified-Since"));
                if (modifiedSince != null) {
                    long modifiedSinceSecs = (long) Math.floor((double) modifiedSince.getTime() / 1000);

                    if (modifiedSinceSecs >= lastModifiedSecs) {
                        // close stream
                        content.getStream().close();

                        // send not modified status code
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        response.setContentLength(0);
                        return true;
                    }
                }

                response.setHeader("Last-Modified", DateTimeHelper.formatHttpDateTime(lastModifiedSecs * 1000));
            }
        }

        // check if cache headers should be set
        if (content instanceof CacheHeaderContentStream) {
            CacheHeaderContentStream chcs = (CacheHeaderContentStream) content;

            if (chcs.getETag() != null) {
                String etag = request.getHeader("If-None-Match");
                if (etag != null && !etag.equals("*")) {
                    if (etag.length() > 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
                        etag = etag.substring(1, etag.length() - 1);
                    }

                    if (chcs.getETag().equals(etag)) {
                        // close stream
                        content.getStream().close();

                        // send not modified status code
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        response.setContentLength(0);
                        return true;
                    }
                }

                response.setHeader("ETag", "\"" + chcs.getETag() + "\"");
            }

            if (chcs.getCacheControl() != null) {
                response.setHeader("Cache-Control", chcs.getCacheControl());
            }

            if (chcs.getExpires() != null) {
                response.setHeader("Expires", DateTimeHelper.formatHttpDateTime(chcs.getExpires()));
            }
        }

        // check if Content-Length header should be set
        if (content instanceof ContentLengthContentStream) {
            if (content.getBigLength() != null && content.getBigLength().signum() >= 0) {
                response.setHeader("Content-Length", content.getBigLength().toString());
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // --- parameters ---
    // -------------------------------------------------------------------------

    /**
     * Extracts a string parameter.
     */
    @SuppressWarnings("unchecked")
    public static String getStringParameter(HttpServletRequest request, String name) {
        if (name == null) {
            return null;
        }

        Map<String, String[]> parameters = (Map<String, String[]>) request.getParameterMap();
        for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
            if (name.equalsIgnoreCase(parameter.getKey())) {
                if (parameter.getValue() == null) {
                    return null;
                }
                return parameter.getValue()[0];
            }
        }

        return null;
    }

    /**
     * Extracts a boolean parameter (with default).
     */
    public static boolean getBooleanParameter(HttpServletRequest request, String name, boolean def) {
        String value = getStringParameter(request, name);
        if ((value == null) || (value.length() == 0)) {
            return def;
        }

        return Boolean.valueOf(value);
    }

    /**
     * Extracts a boolean parameter.
     */
    public static Boolean getBooleanParameter(HttpServletRequest request, String name) {
        String value = getStringParameter(request, name);
        if ((value == null) || (value.length() == 0)) {
            return null;
        }

        return Boolean.valueOf(value);
    }

    /**
     * Extracts an integer parameter (with default).
     */
    public static BigInteger getBigIntegerParameter(HttpServletRequest request, String name, long def) {
        BigInteger result = getBigIntegerParameter(request, name);
        if (result == null) {
            result = BigInteger.valueOf(def);
        }

        return result;
    }

    /**
     * Extracts an integer parameter.
     */
    public static BigInteger getBigIntegerParameter(HttpServletRequest request, String name) {
        String value = getStringParameter(request, name);
        if ((value == null) || (value.length() == 0)) {
            return null;
        }

        try {
            return new BigInteger(value);
        } catch (Exception e) {
            throw new CmisInvalidArgumentException("Invalid parameter '" + name + "'!");
        }
    }

    /**
     * Extracts an enum parameter.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getEnumParameter(HttpServletRequest request, String name, Class<T> clazz) {
        String value = getStringParameter(request, name);
        if ((value == null) || (value.length() == 0)) {
            return null;
        }

        try {
            Method m = clazz.getMethod("fromValue", new Class[] { String.class });
            return (T) m.invoke(null, new Object[] { value });
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw new CmisInvalidArgumentException("Invalid parameter '" + name + "'!");
            }

            throw new CmisRuntimeException(e.getMessage(), e);
        }
    }
}
