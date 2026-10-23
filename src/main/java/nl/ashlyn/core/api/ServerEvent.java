package nl.ashlyn.core.api;

/**
 * Created by Ashlyn on 23/10/2026
 *
 * An event planned by the leaders. Once it has started it stays on the website's timeline.
 * @param name  The event name
 * @param time  Epoch milliseconds the event starts
 * @param recap What happened, written afterwards by the leaders (may be null)
 */
public record ServerEvent(String name, long time, String recap) {

    public boolean hasStarted() {
        return time <= System.currentTimeMillis();
    }

    public ServerEvent withRecap(String recap) {
        return new ServerEvent(name, time, recap);
    }
}
