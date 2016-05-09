package com.idevicesinc.sweetblue;


import com.idevicesinc.sweetblue.utils.Utils_String;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;


public class P_TaskManager
{

    private final LinkedList<P_Task> mTaskQueue;
    private volatile P_Task mCurrent;
    private final BleManager mBleManager;
    private volatile long mUpdateCount;
    private TaskSorter mTaskSorter;


    public P_TaskManager(BleManager mgr)
    {
        mTaskQueue = new LinkedList<>();
        mBleManager = mgr;
        mTaskSorter = new TaskSorter();
    }

    // This returns true if there are tasks in the queue, or there is a task executing currently
    public boolean update(long curTimeMs)
    {
        boolean hasTasks = mTaskQueue.size() > 0 || !isCurrentNull();
        // If the queue has items in it, and there's no current task, get the next task to execute
        if (hasTasks && isCurrentNull())
        {
            mCurrent = mTaskQueue.poll();
            mCurrent.executeTask();
        }
        else
        {
            // Check to see if the current task is interruptible, and if so, check the next item in the queue,
            // which will be the highest priority item in the queue. If it's not higher than the current task,
            // we leave the current task alone. Otherwise, null out the current task, then interrupt it. Then,
            // the higher priority task will get polled on the next update cycle.
            if (!isCurrentNull() && mCurrent.isInterruptible())
            {
                P_Task task = mTaskQueue.peek();
                if (task.hasHigherPriorityThan(mCurrent))
                {
                    P_Task tempTask = mCurrent;
                    mCurrent = null;
                    tempTask.interrupt();
                }
            }
        }
        if (!isCurrentNull())
        {
            mCurrent.update(curTimeMs);
        }
        mUpdateCount++;
        return hasTasks;
    }

    BleManager getManager()
    {
        return mBleManager;
    }

    long getUpdateCount()
    {
        return mUpdateCount;
    }

    private boolean isCurrentNull()
    {
        return mCurrent == null || mCurrent.isNull();
    }


    public void succeedTask(P_Task task)
    {
        if (mCurrent == task)
        {
            mCurrent = null;
        }
    }

    public void failTask(P_Task task)
    {
        if (mCurrent == task)
        {
            mCurrent = null;
        }
    }

    public void add(final P_Task task)
    {
        if (!task.isNull())
        {
            if (getManager().isOnSweetBlueThread())
            {
                add_private(task);
            }
            else
            {
                getManager().mPostManager.postToUpdateThread(new Runnable()
                {
                    @Override public void run()
                    {
                        add_private(task);
                    }
                });
            }
        }
    }

    private void add_private(P_Task task)
    {
        if (mTaskQueue.size() == 0)
        {
            mTaskQueue.add(task);
        }
        else
        {
//            int size = mTaskQueue.size();
//            for (int i = 0; i < size; i++)
//            {
//                if (task.hasHigherPriorityThan(mTaskQueue.get(i)))
//                {
//                    mTaskQueue.add(i, task);
//                    task.addedToQueue();
//                    return;
//                }
//            }

            // If we got here, then the newtask is lower priority than all other tasks in the queue,
            // so we can just add it at the end
            mTaskQueue.add(task);
            Collections.sort(mTaskQueue, mTaskSorter);
        }
        task.addedToQueue();
    }

    private class TaskSorter implements Comparator<P_Task>
    {

        @Override public int compare(P_Task lhs, P_Task rhs)
        {
            int comp = rhs.getPriority().compareTo(lhs.getPriority());
            if (comp != 0)
            {
                return comp;
            }
            else
            {
                if (lhs.requeued() && !rhs.requeued())
                {
                    return -1;
                }
                else if (rhs.requeued())
                {
                    return 1;
                }
                return lhs.timeCreated() < rhs.timeCreated() ? -1 : (lhs.timeCreated() == rhs.timeCreated() ? 0 : 1);
            }
        }
    }

    public void addInterruptedTask(final P_Task task)
    {
        // Add the interrupted task to the beginning of the queue, so that it starts
        // next after the task which interrupted it is done executing.
        if (getManager().isOnSweetBlueThread())
        {
            addInterrupted_private(task);
        }
        else
        {
            getManager().mPostManager.postToUpdateThread(new Runnable()
            {
                @Override public void run()
                {
                    addInterrupted_private(task);
                }
            });
        }
    }

    private void addInterrupted_private(P_Task task)
    {
        if (mTaskQueue.size() == 0)
        {
            mTaskQueue.push(task);
        }
        else
        {
            int size = mTaskQueue.size();
            for (int i = 0; i < size; i++)
            {
                if (task.hasHigherOrTheSamePriorityThan(mTaskQueue.get(i)))
                {
                    mTaskQueue.add(i, task);
                    task.addedToQueue();
                    return;
                }
            }
            // If we got here, it means all other tasks are higher priority, so we'll just add this
            // to the end of the queue.
            mTaskQueue.add(task);
        }
        task.addedToQueue();
    }

    public void cancel(final P_Task task)
    {
        if (getManager().isOnSweetBlueThread())
        {
            cancel_private(task);
        }
        else
        {
            getManager().mPostManager.postToUpdateThread(new Runnable()
            {
                @Override public void run()
                {
                    cancel_private(task);
                }
            });
        }
    }

    private void cancel_private(P_Task task)
    {
        mTaskQueue.remove(task);
        task.onCanceled();
    }

    public P_Task getCurrent()
    {
        return mCurrent != null ? mCurrent : P_Task.NULL;
    }

    void print()
    {
        if (getManager().getLogger().isEnabled())
        {
            getManager().getLogger().i(toString());
        }
    }

    @Override public String toString()
    {
        final String current = mCurrent != null ? mCurrent.toString() : "no current task";

        final String queue = mTaskQueue.size() > 0 ? mTaskQueue.toString() : "[queue empty]";

        return Utils_String.concatStrings(current, " ", queue);
    }
}