package com.todoroo.astrid.voice;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.Toast;

import com.todoroo.andlib.data.Callback;
import com.todoroo.astrid.data.Task;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import ai.api.AIConfiguration;
import ai.api.model.AIError;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.api.ui.AIDialog;

@Singleton
public class ApiAiTasksAgent {

    private Callback<Task> addTaskCallback;
    private AIDialog aiDialog;

    private final AIConfiguration aiConfiguration = new AIConfiguration("7d34f099a1484de1be4dec9fc75d1d0c",
            "cb9693af-85ce-4fbf-844a-5563722fc27f",
            AIConfiguration.SupportedLanguages.English,
            AIConfiguration.RecognitionEngine.System);

    @Inject
    public ApiAiTasksAgent(final Activity activity) {

        aiDialog = new AIDialog(activity, aiConfiguration);

        aiDialog.setResultsListener(new AIDialog.AIDialogListener() {
            @Override
            public void onResult(AIResponse aiResponse) {
                try {
                    if (!aiResponse.isError()) {
                        Result result = aiResponse.getResult();

                        final String action = result.getAction();
                        if (!TextUtils.isEmpty(action)) {
                            if ("task.create".equalsIgnoreCase(action)) {
                                Task newTask = new Task();
                                newTask.setTitle(result.getParameters().get("text").getAsString());

                                if (result.getParameters().containsKey("priority")) {
                                    final String priority = result.getParameters().get("priority").getAsString();

                                    if ("urgent".equalsIgnoreCase(priority)) {
                                        newTask.setImportance(Task.IMPORTANCE_DO_OR_DIE);
                                    } else if ("important".equalsIgnoreCase(priority)) {
                                        newTask.setImportance(Task.IMPORTANCE_MUST_DO);
                                    }

                                }

                                if (result.getParameters().containsKey("date-time")) {
                                    final String dateTimeString = result.getParameters().get("date-time").getAsString();
                                    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, dateFormat.parse(dateTimeString).getTime());
                                } else if (result.getParameters().containsKey("date")) {
                                    final String dateString = result.getParameters().get("date").getAsString();
                                    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY, dateFormat.parse(dateString).getTime());
                                } else if (result.getParameters().containsKey("time")) {
                                    final String timeString = result.getParameters().get("time").getAsString();
                                    final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
                                    final Date timeParameter = timeFormat.parse(timeString);

                                    Calendar taskDueDate = Calendar.getInstance();
                                    taskDueDate.set(Calendar.HOUR_OF_DAY, timeParameter.getHours());
                                    taskDueDate.set(Calendar.MINUTE, timeParameter.getMinutes());
                                    taskDueDate.set(Calendar.SECOND, timeParameter.getSeconds());

                                    newTask.setDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, taskDueDate.getTime().getTime());
                                }

                                if (addTaskCallback != null) {
                                    addTaskCallback.apply(newTask);
                                }

                            } else if ("task.complete".equalsIgnoreCase(action)) {

                            }
                        }
                    }

                    aiDialog.close();

                } catch (ParseException e) {
                    Toast.makeText(activity, e.toString(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(AIError aiError) {
                Toast.makeText(activity, aiError.toString(), Toast.LENGTH_SHORT).show();
                aiDialog.close();
            }
        });
    }

    public void startRecognition() {
        aiDialog.showAndListen();
    }

    public void setAddTaskCallback(Callback<Task> addTaskCallback) {
        this.addTaskCallback = addTaskCallback;
    }
}
