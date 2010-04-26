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
package org.apache.chemistry.opencmis.client.runtime.util;

import org.apache.chemistry.opencmis.client.api.PagingIterable;
import org.apache.chemistry.opencmis.client.api.PagingIterator;

/**
 * Iterable for a CMIS Collection Page
 */
public class PageIterable<T> implements PagingIterable<T> {

    private AbstractPageFetch<T> pageFetch;
    private long skipCount;

    /**
     * Construct
     * 
     * @param pageFetch
     */
    public PageIterable(AbstractPageFetch<T> pageFetch) {
        this(0, pageFetch);
    }

    /**
     * Construct
     * 
     * @param position
     * @param pageFetch
     */
    protected PageIterable(long position, AbstractPageFetch<T> pageFetch) {
        this.pageFetch = pageFetch;
        this.skipCount = position;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Iterable#iterator()
     */
    public PagingIterator<T> iterator() {
        return new PageIterator<T>(skipCount, pageFetch);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.chemistry.opencmis.client.api.util.PagingIterable#skipTo(long)
     */
    public PagingIterable<T> skipTo(long position) {
        return new CollectionIterable<T>(position, pageFetch);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.chemistry.opencmis.client.api.PagingIterable#getPage()
     */
    public PagingIterable<T> getPage() {
        return new PageIterable<T>(skipCount, pageFetch);
    }
}