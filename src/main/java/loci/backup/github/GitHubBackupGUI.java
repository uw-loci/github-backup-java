/*
 * #%L
 * Maven plugin for backing up github state.
 * %%
 * Copyright (C) 2013 - 2014 Board of Regents of the University of
 *           Wisconsin-Madison
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package loci.backup.github;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Swing-based GUI entry point for {@link GitHubBackup}. Harvests user input and
 * passes the parameters to the backup method. Allows the use of GitHubBackup as
 * a .app, .exe, etc...
 * 
 * @author Mark Hiner
 */
public final class GitHubBackupGUI {

	// -- Main method --

	public static void main(final String[] args) throws Exception {
		// Query the user for the base directory
		final JFileChooser opener =
			new JFileChooser(System.getProperty("user.home"));
		opener.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		opener.setDialogTitle("Select a directory to back up");
		final int result = opener.showOpenDialog(null);

		if (result == JFileChooser.APPROVE_OPTION) {
			// User selected a directory, so we continue
			String localDir = opener.getSelectedFile().getAbsolutePath();
			JTextField login = new JTextField();
			JTextField password = new JPasswordField();
			JTextField token = new JTextField();
			JTextField user = new JTextField();
			JTextField repo = new JTextField();

			// Query the user for all the string parameters
			Object[] message =
				{ "Username:", login, "Password:", password, "Token:", token,
					"User to backup:", user, "Repo to backup:", repo };
			int option =
				JOptionPane.showConfirmDialog(null, message, "Backup options (1 of 2)",
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

			if (option == JOptionPane.OK_OPTION) {
				// Query the user for the boolean clean parameter
				int doClean =
					JOptionPane.showConfirmDialog(null, "Force complete backup?",
						"Backup options (2 of 2)", JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE);
				boolean clean = false;
				if (doClean == JOptionPane.OK_OPTION) {
					clean = true;
				}
				// Convert the harvested fields to a properly formatted String[]
				String[] backupArgs =
					BackupTools.makeArgs(localDir, login.getText(), password.getText(),
						token.getText(), user.getText(), repo.getText(), clean);

				// Perform the actual backup
				if (new GitHubBackup().doMain(backupArgs)) {
					JOptionPane.showMessageDialog(null, "Backup complete!");
				}
				else {
					JOptionPane.showMessageDialog(null, "Backup complete.\nThere were "
						+ "errors during the backup process.\nVerify the integrity of your"
						+ " repository and run the backup program again.");
				}
			}
		}
	}

}
