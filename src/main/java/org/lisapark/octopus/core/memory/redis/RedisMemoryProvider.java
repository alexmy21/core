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
package org.lisapark.octopus.core.memory.redis;

import static com.google.common.base.Preconditions.checkArgument;
import java.util.Collection;
import java.util.Map;
import org.lisapark.octopus.core.memory.RedisMemory;
import org.lisapark.octopus.core.memory.RedisProvider;
import org.lisapark.octopus.core.memory.heap.HeapCircularBuffer;

/**
 * @author dave sinclair(david.sinclair@lisa-park.com)
 */
public class RedisMemoryProvider implements RedisProvider {
   
    @Override
    public <T> RedisMemory<T> createCircularBuffer(int bufferSize, Map<String, Object> params) {
        checkArgument(bufferSize > 0, "bufferSize has to be greater than zero");

        return new RedisCircularBuffer<T>(bufferSize, params);
    }
}
