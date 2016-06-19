/* 
 * Copyright (C) 2013 Lisa Park, Inc. (www.lisa-park.net)
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
package org.lisapark.octopus.core.source.external.impl;

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import org.lisapark.octopus.core.Output;
import org.lisapark.octopus.core.Persistable;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.event.Attribute;
import org.lisapark.octopus.core.event.Event;
import org.lisapark.octopus.core.event.EventType;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.runtime.ProcessingRuntime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.lisapark.octopus.core.source.external.CompiledExternalSource;
import org.lisapark.octopus.core.source.external.ExternalSource;

/**
 * @author dave sinclair(david.sinclair@lisa-park.com)
 */
@Persistable
public class WebFileSource extends ExternalSource {

    private static final String DEFAULT_NAME = "Web File source";
    private static final String DEFAULT_DESCRIPTION = "Downloads file from Internet.";

    private static final int URL_PARAMETER_ID = 1;
    private static final int FILE_NAME_PARAMETER_ID = 2;
    private static final int OUT_DIR_PARAMETER_ID = 3;

    public WebFileSource(UUID id, String name, String description) {
        super(id, name, description);
    }

    private WebFileSource(UUID id, WebFileSource copyFromSource) {
        super(id, copyFromSource);
    }

    public WebFileSource(WebFileSource copyFromSource) {
        super(copyFromSource);
    }

    public String getUrl() {
        return getParameter(URL_PARAMETER_ID).getValueAsString();
    }

    private String getFileName() {
        return getParameter(FILE_NAME_PARAMETER_ID).getValueAsString();
    }

    private String getOutDir() {
        return getParameter(OUT_DIR_PARAMETER_ID).getValueAsString();
    }

    @Override
    public WebFileSource copyOf() {
        return new WebFileSource(this);
    }

    @Override
    public WebFileSource newInstance() {
        UUID sourceId = UUID.randomUUID();
        return new WebFileSource(sourceId, this);
    }

    public static WebFileSource newTemplate() {
        UUID sourceId = UUID.randomUUID();

        WebFileSource webFileSource = new WebFileSource(sourceId, DEFAULT_NAME, DEFAULT_DESCRIPTION);
        webFileSource.setOutput(Output.outputWithId(1).setName("Output"));

        webFileSource.addParameter(
                Parameter.stringParameterWithIdAndName(URL_PARAMETER_ID, "URL").
                description("Website URL.").
                defaultValue("http://data.gdeltproject.org/events/"));

        webFileSource.addParameter(
                Parameter.stringParameterWithIdAndName(FILE_NAME_PARAMETER_ID, "File Name").
                description("Web site File Name.").
                defaultValue("20140307.export.CSV.zip"));

        webFileSource.addParameter(
                Parameter.stringParameterWithIdAndName(OUT_DIR_PARAMETER_ID, "Output Dir").
                description("Output directory on the server.").
                defaultValue("/home/alexmy/"));

        return webFileSource;
    }

    @Override
    public CompiledExternalSource compile() throws ValidationException {
        return new CompiledTestSource(copyOf());
    }

    static class CompiledTestSource implements CompiledExternalSource {

        private final WebFileSource source;

        /**
         * Running is declared volatile because it may be access my different
         * threads
         */
        private volatile boolean running;

        public CompiledTestSource(WebFileSource source) {
            this.source = source;
        }

        @Override
        public void startProcessingEvents(ProcessingRuntime runtime) {

            // this needs to be atomic, both the check and set
            synchronized (this) {
                checkState(!running, "Source is already processing events. Cannot call processEvents again");
                running = true;
            }
            
            EventType eventType = source.getOutput().getEventType();
            List<Attribute> attributes = eventType.getAttributes();

            String urlString = source.getUrl();
            String fileName = source.getFileName();
            String outDir = source.getOutDir();

            Map<String, Object> attributeData = Maps.newHashMap();
            int count;

            if (running) {
                try {

                    URL url = new URL(urlString + fileName);
                    URLConnection conn = url.openConnection();

                    String saveTo = outDir + fileName;

                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(saveTo);
                    byte[] b = new byte[1024];

                    while ((count = in.read(b)) >= 0) {
                        out.write(b, 0, count);
                    }

                    out.flush();

                    File file = new File(saveTo);

                    System.out.printf("name = %s, type = %s, length = %s, location = %s",
                            file.getName(), "zip", file.length(), file.getPath());

                    attributeData.put(attributes.get(0).getName(), file.getName());
                    attributeData.put(attributes.get(1).getName(), "zip");
                    attributeData.put(attributes.get(2).getName(), file.length());
                    attributeData.put(attributes.get(3).getName(), file.getPath());

                } catch (IOException e) {
                    System.out.println("Reading file exception.");
                }

                Event e = new Event(attributeData);

                runtime.sendEventFromSource(e, source);
            }

        }

        @Override
        public void stopProcessingEvents() {
            running = false;
        }
    }
}
