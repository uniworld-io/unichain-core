/*
 * unichain-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * unichain-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.unx.common.application;

import org.unx.common.parameter.CommonParameter;
import org.unx.core.ChainBaseManager;
import org.unx.core.config.args.Args;
import org.unx.core.db.Manager;

public interface Application {

  void setOptions(Args args);

  void init(CommonParameter parameter);

  void initServices(CommonParameter parameter);

  void startup();

  void shutdown();

  void startServices();

  void shutdownServices();

  void addService(Service service);

  Manager getDbManager();

  ChainBaseManager getChainBaseManager();

}
