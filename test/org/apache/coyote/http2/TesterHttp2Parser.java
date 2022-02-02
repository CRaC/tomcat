/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http2;

import java.io.IOException;

import org.apache.coyote.http2.Http2TestBase.TestOutput;

/**
 * Expose the parser outside of this package for use in other tests.
 */
public class TesterHttp2Parser extends Http2Parser {

    private final TestOutput output;

    TesterHttp2Parser(String connectionId, Input input, TestOutput output) {
        super(connectionId, input, output);
        this.output = output;
    }

    @Override
    public boolean readFrame(boolean block) throws Http2Exception, IOException {
        return super.readFrame(block);
    }

    @Override
    protected void readPushPromiseFrame(int streamId, int flags, int payloadSize) throws Http2Exception, IOException {

        // Parse flags used in this method
        boolean hasPadding = Flags.hasPadding(flags);
        boolean headersEndStream = Flags.isEndOfStream(flags);

        // Padding size
        int paddingSize = 0;
        if (hasPadding) {
            byte[] bPadSize = new byte[1];
            input.fill(true, bPadSize);
            paddingSize = ByteUtil.getOneByte(bPadSize, 0);
        }

        // Pushed stream ID
        byte[] bPushedStreamId = new byte[4];
        input.fill(true, bPushedStreamId);
        int pushedStreamId = ByteUtil.get31Bits(bPushedStreamId, 0);

        output.pushPromise(streamId, pushedStreamId);

        int headerSize = payloadSize - 4 - paddingSize;
        if (hasPadding) {
            headerSize--;
        }

        HpackDecoder hpackDecoder = output.getHpackDecoder();
        hpackDecoder.setHeaderEmitter(output.headersStart(pushedStreamId, headersEndStream));

        readHeaderPayload(pushedStreamId, headerSize);

        if (hasPadding) {
            swallowPayload(streamId, FrameType.PUSH_PROMISE.getId(), paddingSize, true);
        }
    }
}
