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

import org.lisapark.octopus.core.memory.heap.*;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Map;
import org.lisapark.octopus.core.memory.RedisMemory;
import redis.clients.jedis.Jedis;

/**
 * @author dave sinclair(david.sinclair@lisa-park.com)
 * @param <T>
 */
public class RedisCircularBuffer<T> implements RedisMemory<T> {
 
    private Jedis jedis = null;
    private int bufferSize;

    @SuppressWarnings("unchecked")
    public RedisCircularBuffer(int n, Map<String, Object> params) {
        
        this.jedis = new Jedis("localhost");
        this.bufferSize = n;

    }

    @Override
    public void add(String key, T value) {
        
        jedis.append(key, value.toString());

    }

    @Override
    public boolean remove(String key, T value) {
        throw new UnsupportedOperationException("Remove not supported");
    }

    @Override
    public Collection<T> values() {
        Collection<T> values = Lists.newArrayListWithCapacity(bufferSize);

//        for (T item : buffer) {
//            if (item != null) {
//                values.add(item);
//            }
//        }

        return values;
    }
}
