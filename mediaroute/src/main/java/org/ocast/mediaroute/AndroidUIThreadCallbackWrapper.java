/*
 * Copyright 2019 Orange
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ocast.mediaroute;

import android.os.Handler;
import android.os.Looper;
import org.ocast.core.models.CallbackWrapper;
import org.ocast.core.models.Consumer;

public class AndroidUIThreadCallbackWrapper implements CallbackWrapper {

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public  <T> Consumer<T> wrap(Consumer<T> consumer) {
        return t -> handler.post(() -> consumer.run(t));
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        return () -> handler.post(runnable);
    }
}