package org.apache.chemistry.opencmis.inmemory.storedobj.impl;
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */


import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.data.RenditionData;
import org.apache.chemistry.opencmis.commons.exceptions.CmisInvalidArgumentException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.RenditionDataImpl;
import org.apache.chemistry.opencmis.commons.spi.BindingsObjectFactory;
import org.apache.chemistry.opencmis.inmemory.FilterParser;
import org.apache.chemistry.opencmis.inmemory.NameValidator;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.Folder;
import org.apache.chemistry.opencmis.inmemory.storedobj.api.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderImpl extends StoredObjectImpl implements Folder {
    private static final Logger LOG = LoggerFactory.getLogger(FilingImpl.class.getName());
    protected String parentId;
    
    public FolderImpl() {
        super();
    }

    public FolderImpl(String name, String parentId) {
        super();
        init(name, parentId);
    }


    @Override
    public void fillProperties(Map<String, PropertyData<?>> properties, BindingsObjectFactory objFactory,
            List<String> requestedIds) {

        super.fillProperties(properties, objFactory, requestedIds);

        // add folder specific properties

        if (FilterParser.isContainedInFilter(PropertyIds.PARENT_ID, requestedIds)) {
            properties.put(PropertyIds.PARENT_ID, objFactory.createPropertyIdData(PropertyIds.PARENT_ID,
                    parentId));
        }

        if (FilterParser.isContainedInFilter(PropertyIds.ALLOWED_CHILD_OBJECT_TYPE_IDS, requestedIds)) {
            String allowedChildObjects = null; // TODO: not yet supported
            properties.put(PropertyIds.ALLOWED_CHILD_OBJECT_TYPE_IDS, objFactory.createPropertyIdData(
                    PropertyIds.ALLOWED_CHILD_OBJECT_TYPE_IDS, allowedChildObjects));
        }

//        if (FilterParser.isContainedInFilter(PropertyIds.PATH, requestedIds)) {
//            String path = getPath();
//            properties.put(PropertyIds.PATH, objFactory.createPropertyStringData(PropertyIds.PATH, path));
//        }
    }

    @Override
	public List<String> getAllowedChildObjectTypeIds() {
        // TODO implement this.
        return null;
    }

    @Override
	public List<RenditionData> getRenditions(String renditionFilter, long maxItems, long skipCount) {
        if (null==renditionFilter)
            return null;
        String tokenizer = "[\\s;]";
        String[] formats = renditionFilter.split(tokenizer);
        boolean isImageRendition = testRenditionFilterForImage(formats);
 
        if (isImageRendition) {
            List<RenditionData> renditions = new ArrayList<RenditionData>(1);
            RenditionDataImpl rendition = new RenditionDataImpl();
            rendition.setBigHeight(BigInteger.valueOf(ICON_SIZE));
            rendition.setBigWidth(BigInteger.valueOf(ICON_SIZE));
            rendition.setKind("cmis:thumbnail");
            rendition.setMimeType(RENDITION_MIME_TYPE_PNG);
            rendition.setRenditionDocumentId(getId());
            rendition.setStreamId(getId() + RENDITION_SUFFIX);
            rendition.setBigLength(BigInteger.valueOf(-1L));
            rendition.setTitle(getName());
            rendition.setRenditionDocumentId(getId());
            renditions.add(rendition);
            return renditions;
        } else {
            return null;
        }
    }

    @Override
	public ContentStream getRenditionContent(String streamId, long offset, long length) {
        try {
            return getIconFromResourceDir("/folder.png");
        } catch (IOException e) {
            LOG.error("Failed to generate rendition: ", e);
            throw new CmisRuntimeException("Failed to generate rendition: " + e);
        }
    }

    @Override
	public boolean hasRendition(String user) {
        return true;
    }

    @Override
    public List<String> getParentIds() {
        if (parentId == null)
            return Collections.emptyList();
        else
            return Collections.singletonList(parentId);
    }

    @Override
    public boolean hasParent() {
        return null != parentId;
    }

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public String getPathSegment() {
        return getName();
    }

    @Override
    public void setParentId (String parentId) {
        this.parentId = parentId;
    }
    
    // Helper functions
    private void init(String name, String parentId) {
        if (!NameValidator.isValidName(name)) {
            throw new CmisInvalidArgumentException(NameValidator.ERROR_ILLEGAL_NAME);
        }
        setName(name);
        this.parentId = parentId;;
    }

}
