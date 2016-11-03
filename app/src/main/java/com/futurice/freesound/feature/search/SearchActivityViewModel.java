/*
 * Copyright 2016 Futurice GmbH
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

package com.futurice.freesound.feature.search;

import com.futurice.freesound.common.Text;
import com.futurice.freesound.feature.analytics.Analytics;
import com.futurice.freesound.utils.TextUtils;
import com.futurice.freesound.viewmodel.BaseViewModel;

import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

import static com.futurice.freesound.functional.Functions.nothing1;
import static com.futurice.freesound.utils.Preconditions.get;
import static com.futurice.freesound.rx.TimeScheduler.time;
import static io.reactivex.schedulers.Schedulers.computation;
import static timber.log.Timber.e;

final class SearchActivityViewModel extends BaseViewModel {

    @VisibleForTesting
    static final int SEARCH_DEBOUNCE_TIME_SECONDS = 2;

    static final String NO_SEARCH = Text.EMPTY;

    @VisibleForTesting
    static final String SEARCH_DEBOUNCE_TAG = "SEARCH DEBOUNCE";

    @NonNull
    private final SearchDataModel searchDataModel;

    @NonNull
    private final Analytics analytics;

    @NonNull
    private final Subject<String> searchTermOnceAndStream = BehaviorSubject
            .createDefault(NO_SEARCH);

    SearchActivityViewModel(@NonNull final SearchDataModel searchDataModel,
                            @NonNull final Analytics analytics) {
        this.searchDataModel = get(searchDataModel);
        this.analytics = get(analytics);
    }

    @NonNull
    Observable<Boolean> isClearEnabledOnceAndStream() {
        return searchTermOnceAndStream.observeOn(computation())
                                      .map(SearchActivityViewModel::isCloseEnabled);

    }

    void search(@NonNull final String query) {
        analytics.log("SearchPressedEvent");
        searchTermOnceAndStream.onNext(query.trim());
    }

    @Override
    public void bind(@NonNull final CompositeDisposable d) {
        d.add(searchTermOnceAndStream.observeOn(computation())
                                     .distinctUntilChanged()
                                     .switchMap(query -> TextUtils.isNotEmpty(query)
                                             ? querySearch(query).toObservable()
                                             : searchDataModel.clear().toObservable())
                                     .subscribeOn(computation())
                                     .subscribe(nothing1(),
                                                e -> e(e,
                                                       "Fatal error when setting search term")));
    }

    @NonNull
    private Completable querySearch(@NonNull final String query) {
        return Observable.timer(SEARCH_DEBOUNCE_TIME_SECONDS,
                                TimeUnit.SECONDS,
                                time(SEARCH_DEBOUNCE_TAG))
                         .flatMap(__ -> searchDataModel.querySearch(query).toObservable())
                         .toCompletable();
    }

    private static boolean isCloseEnabled(@NonNull final String query) {
        return TextUtils.isNotNullOrEmpty(query);
    }

}