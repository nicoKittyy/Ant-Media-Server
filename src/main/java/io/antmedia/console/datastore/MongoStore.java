package io.antmedia.console.datastore;


import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClients;

import dev.morphia.Datastore;
import dev.morphia.Morphia;
import dev.morphia.query.experimental.filters.Filters;
import dev.morphia.query.experimental.updates.UpdateOperators;
import io.antmedia.datastore.db.types.User;
import io.antmedia.rest.model.UserType;

public class MongoStore extends AbstractConsoleDataStore {

	private Datastore datastore;

	protected static Logger logger = LoggerFactory.getLogger(MongoStore.class);

	protected volatile boolean available = false;
	private com.mongodb.client.MongoClient mongoClient;
	
	

	public MongoStore(String dbHost, String dbUser, String dbPassword) {
		String dbName = SERVER_STORAGE_MAP_NAME;

		String uri =  io.antmedia.datastore.db.MongoStore.getMongoConnectionUri(dbHost, dbUser, dbPassword);

		mongoClient = MongoClients.create(uri);

		datastore = Morphia.createDatastore(mongoClient, dbName);
		datastore.getMapper().mapPackage("io.antmedia.datastore.db.types");
		
		datastore.ensureIndexes();

		available = true;
	}

	@Override
	public List<User> getUserList(){
		synchronized(this) {
			List<User> users = new ArrayList<>();
			try {
				users = datastore.find(User.class).iterator().toList();
			}
			catch (Exception e) {
				logger.error(ExceptionUtils.getStackTrace(e));
			}
			return users;
		}
	}

	@Override
	public boolean addUser(String username, String password, UserType userType) {
		synchronized(this) {
			boolean result = false;

			if (username != null && password != null && userType != null) {
				User existingUser = datastore.find(User.class).filter(Filters.eq("email", username)).first();
						
				if (existingUser == null) 
				{
					User user = new User(username, password, userType);
					datastore.save(user);
					result = true;
				}
				else {
					logger.warn("user with {} already exist", username);
				}
			}
			return result;
		}
	}

	@Override
	public boolean editUser(String username, String password, UserType userType) {
		synchronized(this) {
			try {
				return datastore.find(User.class)
									.filter(Filters.eq("email", username))
									.update(UpdateOperators.set("password", password), UpdateOperators.set("userType", userType))
									.execute()
									.getMatchedCount() == 1;
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	@Override
	public boolean deleteUser(String username) 
	{
		synchronized(this) {
			try {
				return datastore.find(User.class)
						.filter(Filters.eq("email", username))
						.delete().getDeletedCount() == 1;
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	@Override
	public boolean doesUsernameExist(String username) {
		synchronized(this) {
			User existingUser = datastore.find(User.class).filter(Filters.eq("email", username)).first();

			return existingUser != null;
		}
	}

	/**
	 * This method is for authentication
	 */
	@Override
	public boolean doesUserExist(String username, String password) 
	{
		boolean result = false;
		synchronized(this) 
		{
			User existingUser = datastore.find(User.class).filter(Filters.eq("email", username), Filters.eq("password", password)).first();
			if(existingUser != null)
			{
				result = true;
			}
		}
		return result;
	}

	@Override
	public User getUser(String username) {
		synchronized(this) {
			return datastore.find(User.class).filter(Filters.eq("email", username)).first();
		}
	}

	@Override
	public void clear() {
		synchronized(this) {
			datastore.find(User.class).delete();
		}
	}

	@Override
	public void close() {
		synchronized(this) {
			available = false;
			mongoClient.close();
		}
	}

	@Override
	public int getNumberOfUserRecords() {
		synchronized(this) {
			return (int) datastore.find(User.class).count();
		}
	}

	/**
	 * Return if data store is available. DataStore is available if it's initialized and not closed. 
	 * It's not available if it's closed. 
	 * @return availability of the datastore
	 */
	public boolean isAvailable() {
		return available;
	}

}
