package aqua.blatt7.broker;

import java.util.*;

/*
 * This class is not thread-safe and hence must be used in a thread-safe way, e.g. thread confined or
 * externally synchronized.
 */
public class ClientCollection<T> {

    private class Client {
        final String id;
        final T client;

        Client(String id, T client) {
            this.id = id;
            this.client = client;
        }
    }

    private final List<Client> clients;
    private final Map<String, Long> timestamps;

    public ClientCollection() {
        clients = new ArrayList<>();
        timestamps = new HashMap<>();
    }

    public ClientCollection<T> add(String id, T client) {
        int index = indexOf(id);
        if (index == -1) {
            clients.add(new Client(id, client));
        } else {
            clients.set(index, new Client(id, client)); // aktualisiere Adresse
        }
        timestamps.put(id, System.currentTimeMillis());
        return this;
    }

    public ClientCollection<T> remove(int index) {
        String id = clients.get(index).id;
        timestamps.remove(id);
        clients.remove(index);
        return this;
    }

    public void removeById(String id) {
        int index = indexOf(id);
        if (index != -1) {
            remove(index);
        }
    }

    public int indexOf(String id) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).id.equals(id))
                return i;
        return -1;
    }

    public int indexOf(T client) {
        for (int i = 0; i < clients.size(); i++)
            if (clients.get(i).client.equals(client))
                return i;
        return -1;
    }

    public T getClient(int index) {
        return clients.get(index).client;
    }

    public T get(String id) {
        int index = indexOf(id);
        if (index != -1) {
            return getClient(index);
        }
        return null;
    }

    public int size() {
        return clients.size();
    }

    public T getLeftNeighorOf(int index) {
        return index == 0 ? clients.get(clients.size() - 1).client : clients.get(index - 1).client;
    }

    public T getRightNeighorOf(int index) {
        return index < clients.size() - 1 ? clients.get(index + 1).client : clients.get(0).client;
    }

    public Set<String> getIds() {
        Set<String> ids = new HashSet<>();
        for (Client client : clients) {
            ids.add(client.id);
        }
        return ids;
    }

    public long getTimestamp(String id) {
        return timestamps.getOrDefault(id, 0L);
    }

    public void updateTimestamp(String id) {
        timestamps.put(id, System.currentTimeMillis());
    }

    public boolean containsId(T clientAddress) {
        return indexOf(clientAddress) != -1;
    }
}