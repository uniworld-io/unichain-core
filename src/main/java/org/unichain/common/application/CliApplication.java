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
package org.unichain.common.application;

import org.unichain.core.config.args.Args;
import org.unichain.core.db.store.BlockStore;
import org.unichain.core.db.Manager;

public class CliApplication implements Application {

  @Override
  public void setOptions(Args args) {

  }

  @Override
  public void init(Args args) {

  }

  @Override
  public void initServices(Args args) {

  }

  @Override
  public void startup() {

  }

  @Override
  public void shutdown() {

  }

  @Override
  public void startServices() {

  }

  @Override
  public void shutdownServices() {

  }

  @Override
  public BlockStore getBlockStoreS() {
    return null;
  }

  @Override
  public void addService(Service service) {

  }

  @Override
  public Manager getDbManager() {
    return null;
  }

}
