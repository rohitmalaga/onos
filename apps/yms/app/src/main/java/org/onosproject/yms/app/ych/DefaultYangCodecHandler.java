/*
 * Copyright 2016-present Open Networking Laboratory
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

package org.onosproject.yms.app.ych;

import org.onosproject.yms.app.ych.defaultcodecs.YangCodecRegistry;
import org.onosproject.yms.app.ydt.YdtExtendedBuilder;
import org.onosproject.yms.app.ydt.YdtExtendedContext;
import org.onosproject.yms.app.yob.DefaultYobBuilder;
import org.onosproject.yms.app.ysr.YangSchemaRegistry;
import org.onosproject.yms.app.ytb.DefaultYangTreeBuilder;
import org.onosproject.yms.app.ytb.YtbException;
import org.onosproject.yms.ych.YangCodecHandler;
import org.onosproject.yms.ych.YangCompositeEncoding;
import org.onosproject.yms.ych.YangDataTreeCodec;
import org.onosproject.yms.ych.YangProtocolEncodingFormat;
import org.onosproject.yms.ydt.YdtBuilder;
import org.onosproject.yms.ydt.YdtContext;
import org.onosproject.yms.ydt.YmsOperationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.onosproject.yms.app.ych.defaultcodecs.utils.DefaultCodecUtils.isNonEmpty;


/**
 * Represents implementation of YANG SBI broker interfaces.
 * YCH acts as a broker between YMS and driver/provider.
 */
public class DefaultYangCodecHandler implements YangCodecHandler {

    private static final String E_MODULE_LIST = "The input module or " +
            "sub-module object list cannot be null.";
    private static final String E_DATA_TREE_CODEC = "data tree codec handler" +
            " is null.";

    /**
     * Schema registry for driver.
     */
    private final YangSchemaRegistry schemaRegistry;

    /**
     * Default codecs.
     */
    private final Map<YangProtocolEncodingFormat, YangDataTreeCodec>
            defaultCodecs = new HashMap<>();

    /**
     * Override codec handler.
     */
    private final Map<YangProtocolEncodingFormat, YangDataTreeCodec>
            overrideCodecs = new HashMap<>();

    /**
     * Creates a new YANG codec handler.
     *
     * @param registry YANG schema registry
     */
    public DefaultYangCodecHandler(YangSchemaRegistry registry) {
        schemaRegistry = registry;

        // update the default codecs from codec registry
        Map<YangProtocolEncodingFormat, YangDataTreeCodec> recvCodec =
                YangCodecRegistry.getDefaultCodecs();
        if (!recvCodec.isEmpty()) {
            for (Map.Entry<YangProtocolEncodingFormat, YangDataTreeCodec>
                    codecEntry : recvCodec.entrySet()) {
                defaultCodecs.put(codecEntry.getKey(), codecEntry.getValue());
            }
        }
    }

    private YangDataTreeCodec getAppropriateCodec(
            YangProtocolEncodingFormat dataFormat) {
        YangDataTreeCodec codec = defaultCodecs.get(dataFormat);

        // Check over ridden codec handler is exist or not.
        if (overrideCodecs != null) {
            YangDataTreeCodec overrideCodec = overrideCodecs.get(dataFormat);
            if (overrideCodec != null) {
                codec = overrideCodec;
            }
        }
        return codec;
    }

    @Override
    public void addDeviceSchema(Class<?> yangModule) {
        schemaRegistry.registerApplication(null, yangModule, null);
    }

    @Override
    public String encodeOperation(String rootName,
                                  String rootNamespace,
                                  Map<String, String> tagAttrMap,
                                  List<Object> moduleList,
                                  YangProtocolEncodingFormat dataFormat,
                                  YmsOperationType opType) {

        if (moduleList == null || moduleList.isEmpty()) {
            throw new YchException(E_MODULE_LIST);
        }

        // Get the default codec handler.
        YangDataTreeCodec codec = getAppropriateCodec(dataFormat);
        if (codec == null) {
            throw new YchException(E_DATA_TREE_CODEC);
        }

        // Get yang data tree from YTB for the received objects.
        DefaultYangTreeBuilder builder = new DefaultYangTreeBuilder();
        YdtExtendedBuilder encodedYdt =
                builder.getYdtBuilderForYo(moduleList, rootName,
                                           rootNamespace, opType,
                                           schemaRegistry);

        encodedYdt.setRootTagAttributeMap(tagAttrMap);

        // Get the xml string form codec handler.
        return codec.encodeYdtToProtocolFormat(encodedYdt);
    }

    @Override
    public YangCompositeEncoding encodeCompositeOperation(
            String rootName,
            String rootNamespace,
            Object moduleObject,
            YangProtocolEncodingFormat dataFormat,
            YmsOperationType opType) {

        if (moduleObject == null) {
            throw new YtbException(E_MODULE_LIST);
        }

        // Get the default codec handler.
        YangDataTreeCodec codec = getAppropriateCodec(dataFormat);
        if (codec == null) {
            throw new YchException(E_DATA_TREE_CODEC);
        }

        List<Object> yangModuleList = new ArrayList<>();
        yangModuleList.add(moduleObject);

        // Get yang data tree from YTB for the received objects.
        DefaultYangTreeBuilder builder = new DefaultYangTreeBuilder();
        YdtExtendedBuilder extBuilder =
                builder.getYdtBuilderForYo(yangModuleList,
                                           rootName,
                                           rootNamespace,
                                           opType,
                                           schemaRegistry);


        // Get the composite response from codec handler.
        return codec.encodeYdtToCompositeProtocolFormat(extBuilder);
    }

    @Override
    public List<Object> decode(String inputString,
                               YangProtocolEncodingFormat dataFormat,
                               YmsOperationType opType) {

        YangDataTreeCodec codec = getAppropriateCodec(dataFormat);
        if (codec == null) {
            throw new YchException(E_DATA_TREE_CODEC);
        }

        // Get the YANG data tree
        YdtBuilder ydtBuilder = codec.decodeProtocolDataToYdt(inputString,
                                                              schemaRegistry,
                                                              opType);

        if (ydtBuilder != null) {
            return getObjectList(ydtBuilder.getRootNode());
        }

        return null;
    }

    @Override
    public Object decode(YangCompositeEncoding protoData,
                         YangProtocolEncodingFormat dataFormat,
                         YmsOperationType opType) {

        YangDataTreeCodec codec = getAppropriateCodec(dataFormat);
        if (codec == null) {
            throw new YchException(E_DATA_TREE_CODEC);
        }

        YdtBuilder ydtBuilder =
                codec.decodeCompositeProtocolDataToYdt(protoData,
                                                       schemaRegistry,
                                                       opType);

        // Get the module object by using YANG data tree
        if (ydtBuilder != null) {
            List<Object> objectList = getObjectList(ydtBuilder.getRootNode());
            if (isNonEmpty(objectList)) {
                return objectList.get(0);
            }
        }

        return null;
    }

    @Override
    public void registerOverriddenCodec(YangDataTreeCodec overrideCodec,
                                        YangProtocolEncodingFormat dataFormat) {
        overrideCodecs.put(dataFormat, overrideCodec);
    }

    /**
     * Returns the list of objects from YDT data tree.
     *
     * @param rootNode YDT root node
     * @return returns list of objects
     */
    private List<Object> getObjectList(YdtContext rootNode) {

        if (rootNode == null) {
            // TODO
            return null;
        }

        if (rootNode.getFirstChild() == null) {
            // TODO
            return null;
        }

        YdtContext curNode = rootNode.getFirstChild();
        DefaultYobBuilder builder = new DefaultYobBuilder();
        Object object = builder.getYangObject((YdtExtendedContext) curNode,
                                              schemaRegistry);
        List<Object> objectList = new ArrayList<>();
        objectList.add(object);

        // Check next module is exit or not. If exist get the object for that.
        while (curNode.getNextSibling() != null) {
            curNode = curNode.getNextSibling();
            object = builder.getYangObject((YdtExtendedContext) curNode,
                                           schemaRegistry);
            objectList.add(object);
        }

        return objectList;
    }
}