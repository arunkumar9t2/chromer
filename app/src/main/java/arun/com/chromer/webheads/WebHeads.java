/*
 * Chromer
 * Copyright (C) 2017 Arunkumar
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

package arun.com.chromer.webheads;

import android.app.Notification;
import android.support.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * Created by Arunkumar on 24-02-2017.
 */
public interface WebHeads {
    interface View {
        void updateNotification(@NonNull Notification notification);

        void initializeTrashy();
    }

    class Presenter<V extends View> {
        V view;

        WeakReference<V> viewRef;

        Presenter(@NonNull V view) {
            viewRef = new WeakReference<>(view);
        }

        boolean isViewAttached() {
            return viewRef != null && viewRef.get() != null;
        }

        V getView() {
            return viewRef.get();
        }


        void cleanUp() {

        }
    }
}
