package com.todoroo.astrid.voice;

import android.app.Activity;
import android.text.TextUtils;

import com.todoroo.andlib.data.Callback;
import com.todoroo.astrid.data.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.inject.Inject;
import javax.inject.Singleton;

import ai.api.AIConfiguration;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;

@Singleton
public class ApiAiAssistant {

    private static final Logger log = LoggerFactory.getLogger(ApiAiAssistant.class);

    private AIService aiService;
    private Callback<Task> addTaskCallback;

    @Inject
    public ApiAiAssistant(Activity activity) {

        final AIConfiguration aiConfiguration = new AIConfiguration("7d34f099a1484de1be4dec9fc75d1d0c",
                "cb9693af-85ce-4fbf-844a-5563722fc27f",
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiService = AIService.getService(activity, aiConfiguration);

        aiService.setListener(new AIListener() {
            @Override
            public void onResult(AIResponse aiResponse) {
                try {
                    if (!aiResponse.isError()) {
                        Result result = aiResponse.getResult();

                        final String action = result.getAction();
                        if (!TextUtils.isEmpty(action)) {
                            if ("task_create".equalsIgnoreCase(action)) {
                                Task newTask = new Task();
                                newTask.setTitle(result.getParameters().get("text").getAsString());

                                if (result.getParameters().containsKey("priority")) {
                                    final String priority = result.getParameters().get("priority").getAsString();
                                    if (!TextUtils.isEmpty(priority)) {
                                        if ("urgent".equalsIgnoreCase(priority)) {
                                            newTask.setImportance(Task.IMPORTANCE_DO_OR_DIE);
                                        } else if ("important".equalsIgnoreCase(priority)) {
                                            newTask.setImportance(Task.IMPORTANCE_MUST_DO);
                                        }
                                    }
                                }

                                if (result.getParameters().containsKey("date-time")) {
                                    final String dateTimeString = result.getParameters().get("date-time").getAsString();
                                    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, dateFormat.parse(dateTimeString).getTime());
                                } else if (result.getParameters().containsKey("date")) {
                                    final String dateString = result.getParameters().get("date").getAsString();
                                    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY, dateFormat.parse(dateString).getTime());
                                } else if (result.getParameters().containsKey("time")) {
                                    final String timeString = result.getParameters().get("time").getAsString();
                                    final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ssZ");
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, timeFormat.parse(timeString).getTime());
                                }

                                if (addTaskCallback != null) {
                                    addTaskCallback.apply(newTask);
                                }

                            } else if ("task_complete".equalsIgnoreCase(action)) {

                            }
                        }
                    }
                } catch (ParseException e) {
                    log.error(e.getMessage(), e);
                }
            }

            @Override
            public void onError(AIError aiError) {
                log.error(aiError.toString());
            }

            @Override
            public void onAudioLevel(float v) {

            }

            @Override
            public void onListeningStarted() {
                log.debug("onListeningStarted");
            }

            @Override
            public void onListeningFinished() {
                log.debug("onListeningFinished");
            }
        });

    }

    public void startRecognition() {
        aiService.startListening();
    }

    public void stopRecognition() {
        aiService.stopListening();
    }

    public void setAddTaskCallback(Callback<Task> addTaskCallback) {
        this.addTaskCallback = addTaskCallback;
    }
}
