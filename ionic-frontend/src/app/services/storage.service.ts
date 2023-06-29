import { Injectable } from '@angular/core';
import { Preferences } from '@capacitor/preferences';
import { Group } from '../models/group';

@Injectable({
  providedIn: 'root'
})
export class StorageService {

  constructor() { }

  async setDarkMode(darkMode: boolean) {
    await Preferences.set({
      key: 'darkMode',
      value: JSON.stringify({ darkMode: darkMode })
    })
  }

  async setActiveGroup(activeGroup: Group) {
    await Preferences.set({
      key: 'activeGroup',
      value: JSON.stringify({ activeGroup: activeGroup })
    })
  }

  async getDarkMode() {
    const darkMode = await Preferences.get({ key: 'darkMode' });
    return JSON.parse(darkMode.value).darkMode;
  }

  async getActiveGroup() {
    const activeGroup = await Preferences.get({ key: 'activeGroup' });
    return JSON.parse(activeGroup.value).activeGroup;
  }

}


