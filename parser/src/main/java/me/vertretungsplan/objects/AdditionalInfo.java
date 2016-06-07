/*
 * substitution-schedule-parser - Java library for parsing schools' substitution schedules
 * Copyright (c) 2016 Johan v. Forstner
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package me.vertretungsplan.objects;

import org.jetbrains.annotations.NotNull;

/**
 * Represents an additional information on the substitution schedule. AdditionalInfos are not related to a
 * specific day on the schedule (such as miscellaneous news the school might provide in DSBmobile and similar
 * systems) and/or come from an independent source (such as information about cancellation of classes caused by snow
 * or other extreme weather conditions provided by the Ministry of Education). For messages that don't satisfy one of
 * these conditions, using {@link SubstitutionScheduleDay#addMessage(String)} might be more appropriate.
 */
public class AdditionalInfo {

	private String title;
	private String text;
	private boolean hasInformation;

    /**
     * Get the title of this additional info
     *
     * @return the title
     */
    @NotNull
    public String getTitle() {
        return title;
	}

    /**
     * Set the title of this additional info. Required.
     *
     * @param title the title to set
     */
    public void setTitle(@NotNull String title) {
        this.title = title;
	}

    /**
     * Get the content text of this additional info. May include simple HTML markup (only a subset of the tags is
     * supported, such as {@code <b>bold</b>} and {@code <i>italic</i>}.
     *
     * @return the text
     */
    @NotNull
    public String getText() {
        return text;
	}

    /**
     * Set the content text of this additional info. May include simple HTML markup (only a subset of the tags is
     * supported, such as {@code <b>bold</b>} and {@code <i>italic</i>}. Required.
     *
     * @param text the text to set
     */
    public void setText(@NotNull String text) {
        this.text = text;
	}

    /**
     * Find out if this AdditionalInfo contains urgent information the user of an app should be notified about.
     *
     * @return boolean indicating if this AdditionalInfo contains urgent information
     */
    public boolean hasInformation() {
        return hasInformation;
	}

    /**
     * Set if this AdditionalInfo contains urgent information the user of an app should be notified about.
     *
     * @param hasInformation boolean indicating if this AdditionalInfo contains urgent information
     */
    @SuppressWarnings("SameParameterValue")
	public void setHasInformation(boolean hasInformation) {
		this.hasInformation = hasInformation;
	}

	@SuppressWarnings("NegatedConditionalExpression")
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AdditionalInfo that = (AdditionalInfo) o;

		if (hasInformation != that.hasInformation) return false;
		if (title != null ? !title.equals(that.title) : that.title != null) return false;
		return !(text != null ? !text.equals(that.text) : that.text != null);

	}

	@Override
	public int hashCode() {
		int result = title != null ? title.hashCode() : 0;
		result = 31 * result + (text != null ? text.hashCode() : 0);
		result = 31 * result + (hasInformation ? 1 : 0);
		return result;
	}
}
