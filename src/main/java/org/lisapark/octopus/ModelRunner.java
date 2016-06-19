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
package org.lisapark.octopus;

import com.google.gson.Gson;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import org.lisapark.octopus.core.ModelBean;
import org.lisapark.octopus.core.ProcessingModel;
import org.lisapark.octopus.core.ProcessorBean;
import org.lisapark.octopus.core.ValidationException;
import org.lisapark.octopus.core.compiler.esper.EsperCompiler;
import org.lisapark.octopus.core.parameter.Parameter;
import org.lisapark.octopus.core.processor.Processor;
import org.lisapark.octopus.core.runtime.ProcessingRuntime;
import org.lisapark.octopus.core.sink.external.ExternalSink;
import org.lisapark.octopus.core.source.external.ExternalSource;
import org.openide.util.Exceptions;

/**
 *
 * @author Alex Mylnikov (alexmy@lisa-park.com)
 */
public class ModelRunner {
    
    public static final String MODEL_NAME = "modelname";
    public static final String MODEL_JSON = "modeljson";

    private ProcessingModel model;

    public static void main(String args[]) {

        ModelRunner runner = new ModelRunner();
        String string = "Alex. Mylnikov: - 1947/03/02";

        String canonical = runner.getCanonical(string);

    }

    ModelRunner() {
    }

    /**
     * 
     * @param model 
     */
    public ModelRunner(ProcessingModel model) {
        this.model = model;
    }
 
    /**
     * This constructor serves old version
     *
     * @param model
     * @param sourceParam
     * @param sinkParam
     */
    public ModelRunner(ProcessingModel model, Map sourceParam, Map sinkParam) {

        this.model = model;

        setSourceParams(sourceParam);
        setSinkParams(sinkParam);
    }

    // New version employes the whole model json converted to the ModelBean
    /**
     * New version employs the whole model JSON converted to the ModelBean
     *
     * @param model
     * @param modelBean
     */
    public ModelRunner(ProcessingModel model, ModelBean modelBean) {

        this.model = model;

        if (modelBean != null) {

            // Update source params
            Set<String> sources = modelBean.getSources();

            if (sources != null) {
                for (String proc : sources) {
                    ProcessorBean procBean = new Gson().fromJson(proc, ProcessorBean.class);
                    setSourceParams(procBean.getParams());
                }
            }

            //Update sink params
            Set<String> sinks = modelBean.getSinks();

            if (sinks != null) {
                for (String proc : sinks) {
                    ProcessorBean procBean = new Gson().fromJson(proc, ProcessorBean.class);
                    setSinkParams(procBean.getParams());
                }
            }

            // Update processors params
            Set<String> procs = modelBean.getProcessors();

            if (procs != null) {
                for (String proc : procs) {
                    ProcessorBean procBean = new Gson().fromJson(proc, ProcessorBean.class);
                    setProcessorParams(procBean.getParams());
                }
            }
        }
    }

    /**
     *
     */
    public void runModel() {

        if (getModel() != null) {
            org.lisapark.octopus.core.compiler.Compiler compiler = new EsperCompiler();
            PrintStream stream = new PrintStream(System.out);
            compiler.setStandardOut(stream);
            compiler.setStandardError(stream);

            try {

                ProcessingRuntime runtime = compiler.compile(getModel());

                runtime.start();
                runtime.shutdown();
                
                // Reliese all resources used by the model
                getModel().complete();

            } catch (ValidationException e1) {
                System.out.println(e1.getLocalizedMessage() + "\n");
            }
        }
    }

    //Setters with Map argument
    //==========================================================================
    /**
     *
     * @param sourceParam
     */
    private void setSourceParams(Map sourceParams) {
        if (sourceParams != null) {
            Set<ExternalSource> extSources = this.getModel().getExternalSources();
            for (ExternalSource extSource : extSources) {
                Set<Parameter> params = extSource.getParameters();
                for (Parameter param : params) {
                    // get param name from the ProcessingModel
                    String paramName = param.getName();
                    // get corresponding param name from ModelBean
                    String beanParamName = containsKey(sourceParams, paramName);
                    if (!beanParamName.isEmpty()) {
                        try {
                            param.setValueFromString(sourceParams.get(beanParamName).toString());
                        } catch (ValidationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
        }
    }

    private void setProcessorParams(Map processorParams) {
        if (processorParams != null) {
            Set<Processor> processors = this.getModel().getProcessors();
            for (Processor processor : processors) {
                Set<Parameter> params = processor.getParameters();
                for (Parameter param : params) {
                    // get param name from the ProcessingModel
                    String paramName = param.getName();
                    // get corresponding param name from ModelBean
                    String beanParamName = containsKey(processorParams, paramName);
                    if (!beanParamName.isEmpty()) {
                        try {
                            param.setValueFromString(processorParams.get(beanParamName).toString());
                        } catch (ValidationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
        }
    }

    private void setSinkParams(Map sinkParams) {
        if (sinkParams != null) {
            Set<ExternalSink> extSinks = this.getModel().getExternalSinks();
            for (ExternalSink extSink : extSinks) {
                Set<Parameter> params = extSink.getParameters();
                for (Parameter param : params) {
                    // get param name from the ProcessingModel
                    String paramName = param.getName();
                    // get corresponding param name from ModelBean
                    String beanParamName = containsKey(sinkParams, paramName);
                    if (!beanParamName.isEmpty()) {
                        try {
                            param.setValueFromString(sinkParams.get(beanParamName).toString());
                        } catch (ValidationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                }
            }
        }
    }

    /**
     * Convert strings to the canonical view: only alphanumerical
     *
     * @param string
     * @return
     */
    private String getCanonical(String string) {
        String retString = string.replaceAll("[^a-zA-Z0-9\\s]", "").replaceAll(" ", "");
        return retString.toLowerCase();

    }

    /**
     * Compares two canonical string and returns original name from the map
     *
     * @param map
     * @param string
     * @return
     */
    private String containsKey(Map<String, Object> map, String string) {

        String contains = "";

        for (String entry : map.keySet()) {
            String entryStr = getCanonical(entry);
            String stringStr = getCanonical(string);
            if (entryStr.equalsIgnoreCase(stringStr)) {
                contains = entry;
                break;
            }

        }

        return contains;
    }

    /**
     * @return the model
     */
    public ProcessingModel getModel() {
        return model;
    }

    /**
     * @param model the model to set
     */
    public void setModel(ProcessingModel model) {
        this.model = model;
    }
}
