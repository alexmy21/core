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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.lisapark.octopus.core.Output;
import org.lisapark.octopus.core.Persistable;
import org.lisapark.octopus.core.ProcessingException;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.event.Attribute;
import org.lisapark.octopus.core.event.Event;
import org.lisapark.octopus.core.event.EventType;
import org.lisapark.octopus.core.parameter.Constraints;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.runtime.ProcessingRuntime;
import org.lisapark.octopus.core.source.external.CompiledExternalSource;
import org.lisapark.octopus.core.source.external.ExternalSource;
import org.lisapark.octopus.util.Booleans;
import org.lisapark.octopus.util.cpneo4j.Product;
import org.lisapark.octopus.util.jdbc.Connections;
import org.lisapark.octopus.util.jdbc.ResultSets;
import org.lisapark.octopus.util.jdbc.Statements;

import org.lisapark.octopus.util.json.WJutils;
import org.lisapark.octopus.util.json.WJutils.WJfile;
import org.openide.util.Exceptions;

/**
 * This class is an {@link ExternalSource} that is used to access relational
 * databases. It can be configured with a JDBC Url for the database, username,
 * password, Driver fully qualified class name, and a query to execute.
 * <p/>
 * Currently, the source uses the
 * {@link org.lisapark.octopus.core.Output#getEventType()} to get the names of
 * the columns and types of the columns, but it will probably be changed in the
 * future to support a mapper that takes a {@link ResultSet} and produces an
 * {@link Event}.
 *
 * @author dave sinclair(david.sinclair@lisa-park.com)
 */
@Persistable
public class TestWJfileListSource extends ExternalSource {

    private static final String DEFAULT_NAME = "WJ FileList";
    private static final String DEFAULT_DESCRIPTION = "Access to WJ Fle List.";

    public static final String INDEX = "index";

    private static final int URL_PARAMETER_ID = 1;
    
    private TestWJfileListSource(UUID sourceId, String name, String description) {
        super(sourceId, name, description);
    }

    private TestWJfileListSource(UUID sourceId, TestWJfileListSource copyFromSource) {
        super(sourceId, copyFromSource);
    }

    private TestWJfileListSource(TestWJfileListSource copyFromSource) {
        super(copyFromSource);
    }

    public String getUrl() {
        return getParameter(URL_PARAMETER_ID).getValueAsString();
    }

    public EventType getEventType() {
        return getOutput().getEventType();
    }

    @Override
    public TestWJfileListSource newInstance() {
        UUID sourceId = UUID.randomUUID();
        return new TestWJfileListSource(sourceId, this);
    }

    @Override
    public TestWJfileListSource copyOf() {
        return new TestWJfileListSource(this);
    }

    public static TestWJfileListSource newTemplate() {
        UUID sourceId = UUID.randomUUID();
        TestWJfileListSource wjsource = new TestWJfileListSource(sourceId, DEFAULT_NAME, DEFAULT_DESCRIPTION);

        wjsource.addParameter(Parameter.stringParameterWithIdAndName(URL_PARAMETER_ID, "File List URL")
                .defaultValue("http://api.wayjournal.com/get_roads")
                .required(true));

        wjsource.setOutput(Output.outputWithId(1).setName("File Attributes:"));
        
        wjsource.addAttributeList();

        return wjsource;
    }
    
    private void addAttributeList() {
        try {
            this.getEventType().addAttribute(Attribute.newAttribute(Long.class, INDEX)); 
            this.getEventType().addAttribute(Attribute.newAttribute(String.class, WJfile.FILE_NAME)); 
            this.getEventType().addAttribute(Attribute.newAttribute(String.class, WJfile.FILENAME));
            this.getEventType().addAttribute(Attribute.newAttribute(String.class, WJfile.URL));
            this.getEventType().addAttribute(Attribute.newAttribute(Long.class, WJfile.SIZE));
        } catch (ValidationException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public CompiledExternalSource compile() throws ValidationException {
        validate();

        return new CompiledWJfileListSource(this.copyOf());
    }

    private static class CompiledWJfileListSource implements CompiledExternalSource {

        private final TestWJfileListSource source;

        private volatile boolean running;

        public CompiledWJfileListSource(TestWJfileListSource source) {
            this.source = source;
        }

        @Override
        public void startProcessingEvents(ProcessingRuntime runtime) throws ProcessingException {
            // this needs to be atomic, both the check and set
            synchronized (this) {
                checkState(!running, "Source is already processing events. Cannot call processEvents again");
                running = true;
            }

            WJutils wjutils = new WJutils();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String url = source.getUrl();
            String wjResponse = wjutils.getFileList(url);
            WJutils.WJresponse resp = gson.fromJson(wjResponse, WJutils.WJresponse.class);
            List<WJutils.WJfile> files = Arrays.asList(resp.getFiles());
            
            processFileList(files, runtime);
            
        }

        void processFileList(List<WJutils.WJfile> files, ProcessingRuntime runtime) {
            Thread thread = Thread.currentThread();
            EventType eventType = source.getEventType();

            for (WJutils.WJfile file : files) {
                System.out.println(file);
            }

            Iterator fileIt = files.iterator();
            Long index = 1L;
            while (!thread.isInterrupted() && running && fileIt.hasNext()) {
                WJfile file = (WJfile) fileIt.next();
                Event newEvent = createEventFromWJfile(file, eventType, index++);

                runtime.sendEventFromSource(newEvent, source);
            }
        }

        @Override
        public void stopProcessingEvents() {
            this.running = false;
        }

        Event createEventFromWJfile(WJutils.WJfile file, EventType eventType, Long index) {
            Map<String, Object> attributeValues = Maps.newHashMap();
            Map<String, Object> map = file.toMap();
            
            map.put(source.INDEX, index);

            for (Attribute attribute : eventType.getAttributes()) {
                Class type = attribute.getType();
                String attributeName = attribute.getName();

                if (type == String.class) {
                    String value = (String) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Integer.class) {
                    Integer value = (Integer) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Short.class) {
                    Short value = (Short) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Long.class) {
                    Long value = (Long) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Double.class) {
                    Double value = (Double) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Float.class) {
                    Float value = (Float) map.get(attributeName);
                    attributeValues.put(attributeName, value);

                } else if (type == Boolean.class) {
                    String value = (String) map.get(attributeName);
                    attributeValues.put(attributeName, Booleans.parseBoolean(value));
                } else {
                    throw new IllegalArgumentException(String.format("Unknown attribute type %s", type));
                }
            }

            return new Event(attributeValues);
        }
    }
}
