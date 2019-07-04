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
package org.lisapark.octopus.repository;

import com.google.common.collect.Lists;
import java.util.List;
import org.lisapark.octopus.core.processor.Processor;
import org.lisapark.octopus.core.processor.impl.Addition;
import org.lisapark.octopus.core.processor.impl.And;
import org.lisapark.octopus.core.processor.impl.Crossing;
import org.lisapark.octopus.core.processor.impl.Division;
import org.lisapark.octopus.core.processor.impl.ForecastSRM;
import org.lisapark.octopus.core.processor.impl.LinearRegressionProcessor;
import org.lisapark.octopus.core.processor.impl.Multiplication;
import org.lisapark.octopus.core.processor.impl.Or;
import org.lisapark.octopus.core.processor.impl.PearsonsCorrelationProcessor;
import org.lisapark.octopus.core.processor.impl.PipeDouble;
import org.lisapark.octopus.core.processor.impl.PipeString;
import org.lisapark.octopus.core.processor.impl.PipeStringDouble;
import org.lisapark.octopus.core.processor.impl.RTCcontroller;
import org.lisapark.octopus.core.processor.impl.Sma;
import org.lisapark.octopus.core.processor.impl.Subtraction;
import org.lisapark.octopus.core.processor.impl.Xor;
import org.lisapark.octopus.core.sink.external.ExternalSink;
import org.lisapark.octopus.core.sink.external.impl.ConsoleSink;
import org.lisapark.octopus.core.sink.external.impl.DatabaseSink;
import org.lisapark.octopus.core.source.external.ExternalSource;
import org.lisapark.octopus.core.source.external.impl.Db4oModelsSource;
import org.lisapark.octopus.core.source.external.impl.Db4oProcessorsSource;
import org.lisapark.octopus.core.source.external.impl.Db4oReplicaSource;
import org.lisapark.octopus.core.source.external.impl.Db4oSinksSource;
import org.lisapark.octopus.core.source.external.impl.Db4oSourcesSource;
import org.lisapark.octopus.core.source.external.impl.GdeltZipSource;
import org.lisapark.octopus.core.source.external.impl.KickStarterSource;
import org.lisapark.octopus.core.source.external.impl.RTCSource;
import org.lisapark.octopus.core.source.external.impl.RedisQuittokenSource;
import org.lisapark.octopus.core.source.external.impl.SqlQuerySource;
import org.lisapark.octopus.core.source.external.impl.TestRandomBinarySource;
import org.lisapark.octopus.core.source.external.impl.TestSource;
import org.lisapark.octopus.core.source.external.impl.WebFileSource;

public abstract class AbstractOctopusRepository
        implements OctopusRepository {

    @Override
    public List<ExternalSink> getAllExternalSinkTemplates() {
        return Lists.newArrayList(new ExternalSink[]{
                    ConsoleSink.newTemplate(),                 
                    DatabaseSink.newTemplate()
                    });
    }

    @Override
    public List<ExternalSource> getAllExternalSourceTemplates() {
        return Lists.newArrayList(new ExternalSource[]{
                    Db4oModelsSource.newTemplate(),
                    Db4oSourcesSource.newTemplate(),
                    Db4oSinksSource.newTemplate(),
                    Db4oProcessorsSource.newTemplate(),
                    Db4oReplicaSource.newTemplate(),
                    KickStarterSource.newTemplate(),
                    GdeltZipSource.newTemplate(),
                    RedisQuittokenSource.newTemplate(),
                    RTCSource.newTemplate(),                
                    SqlQuerySource.newTemplate(),
                    TestSource.newTemplate(),
                    TestRandomBinarySource.newTemplate(),
                    WebFileSource.newTemplate()
        });
    }

    @Override
    public List<Processor> getAllProcessorTemplates() {
        return Lists.newArrayList(new Processor[]{
                    Addition.newTemplate(),
                    And.newTemplate(),
                    Crossing.newTemplate(),
                    Division.newTemplate(),
                    ForecastSRM.newTemplate(),
                    LinearRegressionProcessor.newTemplate(),
                    Multiplication.newTemplate(),
                    Or.newTemplate(),
                    PearsonsCorrelationProcessor.newTemplate(),
                    PipeDouble.newTemplate(),
                    PipeString.newTemplate(),
                    PipeStringDouble.newTemplate(),
                    RTCcontroller.newTemplate(),
                    Sma.newTemplate(),
                    Subtraction.newTemplate(),
                    Xor.newTemplate()});
    }
}
